package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.application.payment.PaymentStatus
import com.loopers.application.payment.RequestPaymentCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

class PaymentV1Dto {
    data class RequestPaymentRequest(
        @field:Positive
        val orderId: Long,
        @field:NotBlank
        val cardType: String,
        @field:NotBlank
        val cardNo: String,
    ) {
        fun toCommand(userId: Long): RequestPaymentCommand =
            RequestPaymentCommand(
                orderId = orderId,
                userId = userId,
                cardType = cardType,
                cardNo = cardNo,
            )
    }

    data class PaymentResponse(
        val id: Long,
        val orderId: Long,
        val transactionKey: String?,
        val status: PaymentStatus,
        val reason: String?,
    ) {
        companion object {
            fun from(info: PaymentInfo): PaymentResponse =
                PaymentResponse(
                    id = info.id,
                    orderId = info.orderId,
                    transactionKey = info.transactionKey,
                    status = info.status,
                    reason = info.reason,
                )
        }
    }
}
