package com.loopers.domain.product.event

import java.time.ZonedDateTime
import java.util.UUID

object ProductEvent {
    data class Viewed(
        val productId: Long,
        val brandId: Long,
        val memberId: Long? = null,
        val eventId: String = UUID.randomUUID().toString(),
        val occurredAt: ZonedDateTime = ZonedDateTime.now(),
        val version: Long = occurredAt.toInstant().toEpochMilli(),
    )
}
