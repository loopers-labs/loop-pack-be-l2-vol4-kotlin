package com.loopers.application.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentStatus

data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val transactionKey: String?,
    val amount: Long,
    val status: PaymentStatus,
    val reason: String?,
) {
    companion object {
        fun from(payment: PaymentModel): PaymentInfo {
            return PaymentInfo(
                paymentId = payment.id,
                orderId = payment.orderId,
                transactionKey = payment.transactionKey,
                amount = payment.amount,
                status = payment.status,
                reason = payment.reason,
            )
        }
    }
}
