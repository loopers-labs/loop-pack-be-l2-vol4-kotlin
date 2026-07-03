package com.loopers.application.payment

import com.loopers.application.event.PaymentRequestedEvent
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentApplicationService(
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun createPayment(payment: Payment): Payment {
        return paymentRepository.save(payment)
    }

    @Transactional
    fun createPaymentAndPublishRequest(
        payment: Payment,
        callbackUrl: String,
    ): Payment {
        val savedPayment = paymentRepository.save(payment)
        eventPublisher.publishEvent(
            PaymentRequestedEvent(
                paymentId = savedPayment.id!!,
                orderId = savedPayment.orderId,
                userId = savedPayment.userId,
                callbackUrl = callbackUrl,
            ),
        )
        return savedPayment
    }

    @Transactional
    fun markPgResult(paymentId: Long, transactionKey: String, status: PaymentStatus, reason: String?): Payment {
        if (!paymentRepository.markPgResultIfRequested(paymentId, transactionKey, status, reason)) {
            throw CoreException(ErrorType.CONFLICT, "PG 결과를 반영할 수 없는 상태입니다. paymentId=$paymentId")
        }
        return getPayment(transactionKey)
    }

    @Transactional
    fun markFailedIfRequested(paymentId: Long, reason: String?): Boolean {
        return paymentRepository.markFailedIfRequested(paymentId, reason)
    }

    @Transactional
    fun markSuccess(transactionKey: String, reason: String?): Payment {
        if (!paymentRepository.markSuccessIfPending(transactionKey, reason)) {
            throw CoreException(ErrorType.CONFLICT, "결제 성공 처리할 수 없는 상태입니다. transactionKey=$transactionKey")
        }
        return getPayment(transactionKey)
    }

    @Transactional
    fun markFailed(transactionKey: String, reason: String?): Payment {
        if (!paymentRepository.markFailedIfPending(transactionKey, reason)) {
            throw CoreException(ErrorType.CONFLICT, "결제 실패 처리할 수 없는 상태입니다. transactionKey=$transactionKey")
        }
        return getPayment(transactionKey)
    }

    @Transactional(readOnly = true)
    fun findByOrderId(orderId: Long): Payment? {
        return paymentRepository.findByOrderId(orderId)
    }

    @Transactional(readOnly = true)
    fun getPayment(paymentId: Long): Payment {
        return paymentRepository.findById(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다. paymentId=$paymentId")
    }

    @Transactional(readOnly = true)
    fun getPayment(transactionKey: String): Payment {
        return paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다. transactionKey=$transactionKey")
    }
}
