package com.loopers.application.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus

data class PaymentResult(
    val transactionKey: String,
    val orderId: Long,
    val amount: Long,
    val status: PaymentStatus,
) {
    companion object {
        fun from(payment: Payment): PaymentResult = PaymentResult(
            transactionKey = payment.transactionKey,
            orderId = payment.orderId,
            amount = payment.amount,
            status = payment.status,
        )
    }
}
