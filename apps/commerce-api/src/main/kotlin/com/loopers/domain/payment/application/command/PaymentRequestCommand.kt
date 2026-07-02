package com.loopers.domain.payment.application.command

data class PaymentRequestCommand(
    val userId: Long,
    val orderId: Long,
    val cardType: String,
    val cardNo: String,
)
