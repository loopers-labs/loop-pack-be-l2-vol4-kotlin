package com.loopers.domain.outbox

import java.time.ZonedDateTime

data class Outbox(
    val eventId: String,
    val topic: String,
    val payload: String,
    val status: OutboxStatus,
    val occurredAt: ZonedDateTime,
    val id: Long = 0L,
) {
    companion object {
        fun create(
            eventId: String,
            topic: String,
            payload: String,
            occurredAt: ZonedDateTime,
        ): Outbox = Outbox(
            eventId = eventId,
            topic = topic,
            payload = payload,
            status = OutboxStatus.CREATED,
            occurredAt = occurredAt,
        )
    }
}
