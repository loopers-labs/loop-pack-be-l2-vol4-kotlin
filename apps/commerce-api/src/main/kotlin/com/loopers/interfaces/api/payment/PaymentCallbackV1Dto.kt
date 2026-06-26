package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentCallbackApplicationService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

class PaymentCallbackV1Dto {
    /**
     * HTTP request body sent by the PG simulator after it resolves a transaction.
     */
    data class Request(
        @field:NotBlank
        val transactionKey: String,

        @field:NotBlank
        val orderId: String,

        @field:Positive
        val amount: Long,

        @field:NotBlank
        val status: String,

        val reason: String?,
    ) {
        /** Converts the simulator callback payload into the application callback command. */
        fun toCommand(): PaymentCallbackApplicationService.Command =
            PaymentCallbackApplicationService.Command(
                transactionKey = transactionKey,
                orderId = orderId.toLong(),
                amount = amount,
                status = status,
                reason = reason,
            )
    }
}
