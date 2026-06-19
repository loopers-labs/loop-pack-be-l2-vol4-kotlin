package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "payments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payments_order_id", columnNames = ["order_id"]),
        UniqueConstraint(name = "uk_payments_provider_payment_request_id", columnNames = ["pg_provider", "payment_request_id"]),
        UniqueConstraint(name = "uk_payments_provider_payment_key", columnNames = ["pg_provider", "payment_key"]),
        UniqueConstraint(name = "uk_payments_provider_pg_transaction_id", columnNames = ["pg_provider", "pg_transaction_id"]),
    ],
)
class Payment(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: PaymentStatus = PaymentStatus.READY,

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 30)
    val pgProvider: PgProvider,

    @Column(name = "payment_request_id", nullable = false, length = 100)
    val paymentRequestId: String,

    @Column(name = "requested_amount", nullable = false)
    val requestedAmount: Long,

    @Column(name = "payment_key", length = 100)
    var paymentKey: String? = null,

    @Column(name = "pg_transaction_id", length = 100)
    var pgTransactionId: String? = null,

    @Column(name = "approved_amount")
    var approvedAmount: Long? = null,

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null,

    @Column(name = "completion_retry_count", nullable = false)
    var completionRetryCount: Int = 0,

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null,

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null,

    @Column(name = "last_failed_at")
    var lastFailedAt: LocalDateTime? = null,
) : BaseEntity() {
    init {
        if (paymentRequestId.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "결제 요청 식별자는 비어있을 수 없습니다.")
        if (requestedAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "결제 요청 금액은 0 미만일 수 없습니다.")
        if (approvedAmount != null && approvedAmount!! < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "승인 금액은 0 미만일 수 없습니다.")
        }
        if (completionRetryCount < 0) throw CoreException(ErrorType.BAD_REQUEST, "완료 재시도 횟수는 0 미만일 수 없습니다.")
    }

    fun recordApproveRequested(paymentKey: String) {
        if (paymentKey.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "PG paymentKey는 비어있을 수 없습니다.")
        if (status != PaymentStatus.READY && status != PaymentStatus.VERIFY_FAILED) {
            throw CoreException(ErrorType.CONFLICT, "승인 요청을 기록할 수 없는 결제 상태입니다.")
        }
        this.paymentKey = paymentKey
        failureReason = null
    }

    fun approve(pgTransactionId: String, approvedAmount: Long) {
        if (pgTransactionId.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "PG 거래 식별자는 비어있을 수 없습니다.")
        if (approvedAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "승인 금액은 0 미만일 수 없습니다.")
        status = PaymentStatus.APPROVED
        this.pgTransactionId = pgTransactionId
        this.approvedAmount = approvedAmount
        approvedAt = LocalDateTime.now()
        failureReason = null
    }

    fun markVerifyFailed(reason: String) {
        status = PaymentStatus.VERIFY_FAILED
        failureReason = reason.take(500)
        lastFailedAt = LocalDateTime.now()
    }

    fun markCompletionFailed(reason: String) {
        status = PaymentStatus.COMPLETION_FAILED
        failureReason = reason.take(500)
        lastFailedAt = LocalDateTime.now()
    }

    fun incrementCompletionRetryFailure(reason: String) {
        completionRetryCount += 1
        markCompletionFailed(reason)
    }

    fun expire() {
        if (status != PaymentStatus.READY && status != PaymentStatus.VERIFY_FAILED) {
            throw CoreException(ErrorType.CONFLICT, "만료할 수 없는 결제 상태입니다.")
        }
        status = PaymentStatus.EXPIRED
    }

    fun cancel() {
        if (status != PaymentStatus.READY && status != PaymentStatus.APPROVED && status != PaymentStatus.COMPLETION_FAILED) {
            throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 결제 상태입니다.")
        }
        status = PaymentStatus.CANCELED
        canceledAt = LocalDateTime.now()
    }
}
