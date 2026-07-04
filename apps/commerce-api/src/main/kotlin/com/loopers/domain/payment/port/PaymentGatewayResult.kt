package com.loopers.domain.payment.port

data class PaymentGatewayResult(
    val transactionKey: String,
    val status: PaymentGatewayStatus,
    val reason: String?,
)
