package com.loopers.domain.event.model

import java.time.ZonedDateTime

class EventOutbox(
    val id: Long = 0L,
    val eventId: String,
    val topic: String,
    val partitionKey: String,
    val eventType: String,
    val payload: String,
    val status: EventOutboxStatus = EventOutboxStatus.PENDING,
    val retryCount: Int = 0,
    val publishedAt: ZonedDateTime? = null,
) {
    fun published(): EventOutbox {
        return EventOutbox(
            id = id,
            eventId = eventId,
            topic = topic,
            partitionKey = partitionKey,
            eventType = eventType,
            payload = payload,
            status = EventOutboxStatus.PUBLISHED,
            retryCount = retryCount,
            publishedAt = ZonedDateTime.now(),
        )
    }

    fun failed(): EventOutbox {
        return EventOutbox(
            id = id,
            eventId = eventId,
            topic = topic,
            partitionKey = partitionKey,
            eventType = eventType,
            payload = payload,
            status = EventOutboxStatus.PENDING,
            retryCount = retryCount + 1,
            publishedAt = publishedAt,
        )
    }
}

enum class EventOutboxStatus {
    PENDING,
    PUBLISHED,
}
