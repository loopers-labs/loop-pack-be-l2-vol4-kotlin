package com.loopers.domain.payment.application.command

data class PaymentCallbackCommand(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)
