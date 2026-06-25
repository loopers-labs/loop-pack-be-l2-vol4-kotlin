package com.loopers.domain.payment.presentation.response

import com.loopers.domain.payment.application.info.PaymentInfo

data class PaymentResponse(
    val id: Long,
    val orderId: Long,
    val transactionKey: String?,
    val status: String,
    val failureReason: String?,
) {
    companion object {
        fun from(info: PaymentInfo): PaymentResponse = PaymentResponse(
            id = info.id,
            orderId = info.orderId,
            transactionKey = info.transactionKey,
            status = info.status,
            failureReason = info.failureReason,
        )
    }
}
