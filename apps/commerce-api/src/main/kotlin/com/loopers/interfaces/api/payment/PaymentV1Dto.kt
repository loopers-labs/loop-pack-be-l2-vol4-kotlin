package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.domain.payment.PaymentStatus

class PaymentV1Dto {

    data class PaymentRequest(
        val orderId: Long,
        val cardType: String,
        val cardNo: String,
    )

    data class PaymentResponse(
        val paymentId: Long,
        val orderId: Long,
        val transactionKey: String?,
        val amount: Long,
        val status: PaymentStatus,
    ) {
        companion object {
            fun from(info: PaymentInfo) = PaymentResponse(
                paymentId = info.paymentId,
                orderId = info.orderId,
                transactionKey = info.transactionKey,
                amount = info.amount,
                status = info.status,
            )
        }
    }

    data class CallbackRequest(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    )
}
