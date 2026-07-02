package com.loopers.application.payment

import com.loopers.domain.payment.CardType

data class RequestPaymentCommand(
    val loginId: String,
    val password: String,
    val orderId: Long,
    val cardType: CardType,
    val cardNo: String,
)
