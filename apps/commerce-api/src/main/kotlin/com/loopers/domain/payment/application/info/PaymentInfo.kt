package com.loopers.domain.payment.application.info

import com.loopers.domain.payment.model.PaymentModel

data class PaymentInfo(
    val id: Long,
    val orderId: Long,
    val transactionKey: String?,
    val status: String,
    val failureReason: String?,
) {
    companion object {
        fun from(payment: PaymentModel): PaymentInfo = PaymentInfo(
            id = payment.id,
            orderId = payment.orderId,
            transactionKey = payment.externalTransactionKey,
            status = payment.status.name,
            failureReason = payment.failureReason,
        )
    }
}
