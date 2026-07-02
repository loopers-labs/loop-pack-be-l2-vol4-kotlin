package com.loopers.application.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentStatus

data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val status: PaymentStatus,
    val transactionKey: String?,
) {
    companion object {
        fun from(payment: PaymentModel): PaymentInfo = PaymentInfo(
            paymentId = payment.id,
            orderId = payment.orderId,
            status = payment.status,
            transactionKey = payment.transactionKey,
        )
    }
}
