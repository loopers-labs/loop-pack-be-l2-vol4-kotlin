package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentEvent
import com.loopers.domain.payment.PaymentEventRepository
import com.loopers.domain.payment.PaymentEventType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgProvider
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class PaymentApplicationService(
    private val paymentRepository: PaymentRepository,
    private val paymentEventRepository: PaymentEventRepository,
) {
    @Transactional
    fun createReady(orderId: Long, requestedAmount: Long): PaymentInfo {
        val payment = paymentRepository.save(
            Payment(
                orderId = orderId,
                pgProvider = PgProvider.PG_SIMULATOR,
                paymentRequestId = "order-$orderId-${UUID.randomUUID()}",
                requestedAmount = requestedAmount,
            ),
        )
        appendEvent(
            payment,
            PaymentEventType.REQUEST_CREATED,
            pgStatus = null,
            failureReason = null,
            rawResponseSummary = "payment request created",
        )
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordApproveRequested(orderId: Long, paymentKey: String, pgTransactionId: String): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.recordApproveRequested(paymentKey)
        payment.pgTransactionId = pgTransactionId
        appendEvent(
            payment,
            PaymentEventType.APPROVE_REQUESTED,
            pgStatus = null,
            failureReason = null,
            rawResponseSummary = "approve requested",
        )
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordApproveSucceeded(
        orderId: Long,
        pgTransactionId: String,
        approvedAmount: Long,
        pgStatus: String,
        rawResponseSummary: String,
    ): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        if (payment.requestedAmount != approvedAmount) {
            payment.markVerifyFailed("결제 금액이 주문 금액과 일치하지 않습니다.")
            appendEvent(payment, PaymentEventType.APPROVE_FAILED, pgStatus, payment.failureReason, rawResponseSummary)
            return PaymentInfo.from(payment)
        }
        payment.approve(pgTransactionId, approvedAmount)
        appendEvent(payment, PaymentEventType.APPROVE_SUCCEEDED, pgStatus, null, rawResponseSummary)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordApproveFailed(orderId: Long, pgStatus: String, failureReason: String, rawResponseSummary: String): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.markVerifyFailed(failureReason)
        appendEvent(payment, PaymentEventType.APPROVE_FAILED, pgStatus, failureReason, rawResponseSummary)
        return PaymentInfo.from(payment)
    }

    @Transactional(readOnly = true)
    fun getByOrderId(orderId: Long): PaymentInfo =
        PaymentInfo.from(
            paymentRepository.findByOrderId(orderId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다."),
        )

    @Transactional
    fun recordVerifyRequested(orderId: Long): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        if (payment.status != PaymentStatus.COMPLETION_FAILED) {
            throw CoreException(ErrorType.CONFLICT, "완료 실패 결제만 검증 재시도할 수 있습니다.")
        }
        if (payment.completionRetryCount >= 3) {
            throw CoreException(ErrorType.CONFLICT, "결제 완료 재시도 횟수를 초과했습니다.")
        }
        appendEvent(payment, PaymentEventType.VERIFY_REQUESTED, null, null, "verify requested")
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordVerifySucceeded(
        orderId: Long,
        pgTransactionId: String,
        approvedAmount: Long,
        pgStatus: String,
        rawResponseSummary: String,
    ): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        if (payment.requestedAmount != approvedAmount) {
            payment.markVerifyFailed("결제 금액이 주문 금액과 일치하지 않습니다.")
            appendEvent(payment, PaymentEventType.VERIFY_FAILED, pgStatus, payment.failureReason, rawResponseSummary)
            return PaymentInfo.from(payment)
        }
        payment.approve(pgTransactionId, approvedAmount)
        appendEvent(payment, PaymentEventType.VERIFY_SUCCEEDED, pgStatus, null, rawResponseSummary)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordVerifyFailed(orderId: Long, pgStatus: String, failureReason: String, rawResponseSummary: String): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.markVerifyFailed(failureReason)
        appendEvent(payment, PaymentEventType.VERIFY_FAILED, pgStatus, failureReason, rawResponseSummary)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun markCompletionFailed(orderId: Long, reason: String): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.markCompletionFailed(reason)
        appendEvent(payment, PaymentEventType.COMPLETION_FAILED, null, reason, "internal completion failed")
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun incrementCompletionRetryFailure(orderId: Long, reason: String): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.incrementCompletionRetryFailure(reason)
        appendEvent(payment, PaymentEventType.COMPLETION_FAILED, null, reason, "internal completion retry failed")
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun expire(orderId: Long): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.expire()
        appendEvent(payment, PaymentEventType.EXPIRED, null, null, "reservation expired")
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun cancelReady(orderId: Long): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.cancel()
        appendEvent(payment, PaymentEventType.CANCEL_SUCCEEDED, null, null, "payment canceled before pg approve")
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordCancelRequested(orderId: Long): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        if (payment.status != PaymentStatus.APPROVED) {
            throw CoreException(ErrorType.CONFLICT, "승인된 결제만 취소할 수 있습니다.")
        }
        appendEvent(payment, PaymentEventType.CANCEL_REQUESTED, null, null, "cancel requested")
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordCancelSucceeded(orderId: Long, pgStatus: String, rawResponseSummary: String): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.cancel()
        appendEvent(payment, PaymentEventType.CANCEL_SUCCEEDED, pgStatus, null, rawResponseSummary)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun recordCancelFailed(orderId: Long, pgStatus: String, failureReason: String, rawResponseSummary: String): PaymentInfo {
        val payment = getPaymentForUpdate(orderId)
        payment.markCompletionFailed(failureReason)
        appendEvent(payment, PaymentEventType.CANCEL_FAILED, pgStatus, failureReason, rawResponseSummary)
        return PaymentInfo.from(payment)
    }

    private fun getPaymentForUpdate(orderId: Long): Payment =
        paymentRepository.findByOrderIdForUpdate(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.")

    private fun appendEvent(
        payment: Payment,
        eventType: PaymentEventType,
        pgStatus: String?,
        failureReason: String?,
        rawResponseSummary: String?,
    ) {
        paymentEventRepository.save(
            PaymentEvent(
                orderId = payment.orderId,
                paymentId = payment.id.takeIf { it != 0L },
                eventType = eventType,
                pgProvider = payment.pgProvider,
                paymentRequestId = payment.paymentRequestId,
                paymentKey = payment.paymentKey,
                pgTransactionId = payment.pgTransactionId,
                requestedAmount = payment.requestedAmount,
                approvedAmount = payment.approvedAmount,
                pgStatus = pgStatus,
                failureReason = failureReason?.take(500),
                rawResponseSummary = rawResponseSummary?.take(1000),
            ),
        )
    }
}
