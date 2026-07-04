package com.loopers.domain.payment.presentation.request

import com.loopers.domain.payment.application.command.PaymentCallbackCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class PaymentCallbackRequest(
    @field:NotBlank
    val transactionKey: String,
    @field:NotBlank
    @field:Pattern(regexp = "PENDING|SUCCESS|FAILED")
    val status: String,
    val reason: String?,
) {
    fun toCommand(): PaymentCallbackCommand = PaymentCallbackCommand(
        transactionKey = transactionKey,
        status = status,
        reason = reason,
    )
}
