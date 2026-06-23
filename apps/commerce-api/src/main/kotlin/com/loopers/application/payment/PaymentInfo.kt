package com.loopers.application.payment

import com.loopers.domain.payment.Payment

data class PaymentInfo(
    val id: Long,
    val orderId: Long,
    val transactionKey: String,
    val status: PaymentStatus,
    val reason: String?,
) {
    companion object {
        fun from(payment: Payment): PaymentInfo {
            return PaymentInfo(
                id = payment.id!!,
                orderId = payment.orderId,
                transactionKey = payment.transactionKey,
                status = payment.status,
                reason = payment.reason,
            )
        }
    }
}
