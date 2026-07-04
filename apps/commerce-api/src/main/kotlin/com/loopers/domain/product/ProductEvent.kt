package com.loopers.domain.product

import java.time.ZonedDateTime

object ProductEvent {
    data class Viewed(
        val eventId: String,
        val productId: Long,
        val userId: Long?,
        val occurredAt: ZonedDateTime,
    )
}
