package com.loopers.support.event

internal data class PaymentOrderPayload(
    val orderId: Long,
    val paymentId: Long,
)
