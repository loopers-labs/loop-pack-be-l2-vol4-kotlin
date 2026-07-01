package com.loopers.domain.payment.presentation.request

import com.loopers.domain.payment.application.command.PaymentCallbackCommand
import com.loopers.domain.payment.application.command.PaymentRequestCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive

data class PaymentRequest(
    @field:Positive
    val orderId: Long,
    @field:NotBlank
    @field:Pattern(regexp = "SAMSUNG|KB|HYUNDAI")
    val cardType: String,
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")
    val cardNo: String,
) {
    fun toCommand(userId: Long): PaymentRequestCommand = PaymentRequestCommand(
        userId = userId,
        orderId = orderId,
        cardType = cardType,
        cardNo = cardNo,
    )
}

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
