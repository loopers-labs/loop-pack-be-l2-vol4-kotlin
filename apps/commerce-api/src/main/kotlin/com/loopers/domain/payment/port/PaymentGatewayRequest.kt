package com.loopers.domain.payment.port

data class PaymentGatewayRequest(
    val userId: Long,
    val orderId: Long,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)
