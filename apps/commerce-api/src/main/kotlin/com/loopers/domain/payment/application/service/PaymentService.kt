package com.loopers.domain.payment.application.service

import com.loopers.domain.payment.exception.PaymentDomainException
import com.loopers.domain.payment.model.OutboxEventModel
import com.loopers.domain.payment.model.OutboxEventType
import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.port.OutboxRepository
import com.loopers.domain.payment.port.PaymentRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class PaymentTransitionResult(
    val payment: PaymentModel,
    val changed: Boolean,
)

@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val outboxRepository: OutboxRepository,
) {
    @Transactional
    fun request(orderId: Long): PaymentModel =
        paymentRepository.findByOrderIdOrNull(orderId)
            ?: paymentRepository.save(PaymentModel.request(orderId))

    @Transactional
    fun assignTransactionKey(orderId: Long, transactionKey: String): PaymentModel {
        val payment = getByOrderIdForUpdate(orderId)
        return paymentRepository.save(payment.assignTransactionKey(transactionKey))
    }

    @Transactional
    fun markUnknown(orderId: Long, reason: String?): PaymentModel {
        val payment = paymentRepository.save(getByOrderId(orderId).markUnknown(reason))
        outboxRepository.save(
            OutboxEventModel(
                type = OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED,
                aggregateId = payment.id,
                payload = """{"paymentId":${payment.id},"orderId":${payment.orderId}}""",
            ),
        )
        return payment
    }

    @Transactional
    fun approveByTransactionKey(transactionKey: String): PaymentTransitionResult {
        val payment = getByTransactionKeyForUpdate(transactionKey)
        val transitioned = payment.approve(transactionKey)
        val changed = payment.status != transitioned.status
        val saved = paymentRepository.save(transitioned)
        if (changed) {
            savePaymentResultEvent(saved, OutboxEventType.PAYMENT_APPROVED)
        }
        return PaymentTransitionResult(saved, changed)
    }

    @Transactional
    fun failByTransactionKey(transactionKey: String, reason: String?): PaymentTransitionResult {
        val payment = getByTransactionKeyForUpdate(transactionKey)
        val transitioned = payment.fail(reason)
        val changed = payment.status != transitioned.status
        val saved = paymentRepository.save(transitioned)
        if (changed) {
            savePaymentResultEvent(saved, OutboxEventType.PAYMENT_FAILED)
        }
        return PaymentTransitionResult(saved, changed)
    }

    @Transactional(readOnly = true)
    fun getByOrderId(orderId: Long): PaymentModel =
        paymentRepository.findByOrderIdOrNull(orderId) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun getByOrderIdForUpdate(orderId: Long): PaymentModel =
        paymentRepository.findByOrderIdForUpdateOrNull(orderId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getByTransactionKey(transactionKey: String): PaymentModel =
        paymentRepository.findByExternalTransactionKeyOrNull(transactionKey) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun getByTransactionKeyForUpdate(transactionKey: String): PaymentModel =
        paymentRepository.findByExternalTransactionKeyForUpdateOrNull(transactionKey) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun savePaymentResultEvent(payment: PaymentModel, type: OutboxEventType) {
        try {
            outboxRepository.save(
                OutboxEventModel(
                    type = type,
                    aggregateId = payment.id,
                    payload = """{"paymentId":${payment.id},"orderId":${payment.orderId},"status":"${payment.status}"}""",
                ),
            )
        } catch (e: PaymentDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
    }
}
