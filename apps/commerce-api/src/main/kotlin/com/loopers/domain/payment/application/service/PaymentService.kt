package com.loopers.domain.payment.application.service

import com.loopers.domain.payment.application.result.PaymentTransitionResult
import com.loopers.domain.payment.constant.PaymentOutboxAggregate
import com.loopers.domain.payment.constant.PaymentOutboxEventType
import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.port.PaymentRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxRepository
import java.time.ZonedDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentService(private val paymentRepository: PaymentRepository, private val outboxRepository: OutboxRepository) {
    private val log = LoggerFactory.getLogger(PaymentService::class.java)

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
                type = PaymentOutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED.name,
                aggregateType = PaymentOutboxAggregate.TYPE,
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
            savePaymentResultEvent(saved, PaymentOutboxEventType.PAYMENT_APPROVED.name)
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
            savePaymentResultEvent(saved, PaymentOutboxEventType.PAYMENT_FAILED.name)
        }
        return PaymentTransitionResult(saved, changed)
    }

    @Transactional(readOnly = true)
    fun getById(paymentId: Long): PaymentModel =
        paymentRepository.findByIdOrNull(paymentId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getByOrderId(orderId: Long): PaymentModel =
        paymentRepository.findByOrderIdOrNull(orderId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun findPendingSyncEvents(): List<OutboxEventModel> =
        outboxRepository.findPendingByType(PaymentOutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED.name)

    @Transactional
    fun markEventProcessed(eventId: UUID) {
        outboxRepository.markPublished(eventId, ZonedDateTime.now())
    }

    @Transactional
    fun consumeResultEvents(): Int {
        val events = outboxRepository.findPendingByType(PaymentOutboxEventType.PAYMENT_APPROVED.name) +
            outboxRepository.findPendingByType(PaymentOutboxEventType.PAYMENT_FAILED.name)
        events.forEach { event ->
            log.info("결제 결과 이벤트 소비: type={}, aggregateId={}, payload={}", event.type, event.aggregateId, event.payload)
            outboxRepository.markPublished(event.eventId, ZonedDateTime.now())
        }
        return events.size
    }

    private fun getByOrderIdForUpdate(orderId: Long): PaymentModel =
        paymentRepository.findByOrderIdForUpdateOrNull(orderId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getByTransactionKey(transactionKey: String): PaymentModel =
        paymentRepository.findByExternalTransactionKeyOrNull(transactionKey) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun getByTransactionKeyForUpdate(transactionKey: String): PaymentModel =
        paymentRepository.findByExternalTransactionKeyForUpdateOrNull(transactionKey) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun savePaymentResultEvent(payment: PaymentModel, type: String) {
        outboxRepository.save(
            OutboxEventModel(
                type = type,
                aggregateType = PaymentOutboxAggregate.TYPE,
                aggregateId = payment.id,
                payload = """{"paymentId":${payment.id},"orderId":${payment.orderId},"status":"${payment.status}"}""",
            ),
        )
    }
}
