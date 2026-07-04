package com.loopers.domain.payment

import java.time.ZonedDateTime

object PaymentEvent {
    data class Confirmed(
        val eventId: String,
        val orderId: Long,
        val occurredAt: ZonedDateTime,
    )

    data class Failed(
        val eventId: String,
        val orderId: Long,
        val reason: String?,
        val occurredAt: ZonedDateTime,
    )
}
