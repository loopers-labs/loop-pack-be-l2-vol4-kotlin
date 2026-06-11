package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgProvider

data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val status: PaymentStatus,
    val pgProvider: PgProvider,
    val paymentRequestId: String,
    val paymentKey: String?,
    val pgTransactionId: String?,
    val requestedAmount: Long,
    val approvedAmount: Long?,
    val completionRetryCount: Int,
) {
    companion object {
        fun from(payment: Payment) = PaymentInfo(
            paymentId = payment.id,
            orderId = payment.orderId,
            status = payment.status,
            pgProvider = payment.pgProvider,
            paymentRequestId = payment.paymentRequestId,
            paymentKey = payment.paymentKey,
            pgTransactionId = payment.pgTransactionId,
            requestedAmount = payment.requestedAmount,
            approvedAmount = payment.approvedAmount,
            completionRetryCount = payment.completionRetryCount,
        )
    }
}
