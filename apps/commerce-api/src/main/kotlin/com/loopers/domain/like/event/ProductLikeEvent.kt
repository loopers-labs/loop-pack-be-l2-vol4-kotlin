package com.loopers.domain.like.event

import java.time.ZonedDateTime
import java.util.UUID

object ProductLikeEvent {
    data class Like(
        val memberId: Long,
        val productId: Long,
        val brandId: Long,
        val eventId: String = UUID.randomUUID().toString(),
        val occurredAt: ZonedDateTime = ZonedDateTime.now(),
        val version: Long = occurredAt.toInstant().toEpochMilli(),
    )

    data class Unlike(
        val memberId: Long,
        val productId: Long,
        val brandId: Long,
        val eventId: String = UUID.randomUUID().toString(),
        val occurredAt: ZonedDateTime = ZonedDateTime.now(),
        val version: Long = occurredAt.toInstant().toEpochMilli(),
    )
}
