package com.loopers.support.event

import java.time.ZonedDateTime

data class PaymentFailedApplicationEvent(
    val paymentId: Long,
    val orderId: Long,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
