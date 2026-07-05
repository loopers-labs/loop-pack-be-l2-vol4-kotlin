package com.loopers.infrastructure.event.mapper

import com.loopers.domain.event.model.EventOutbox
import com.loopers.infrastructure.event.entity.EventOutboxEntity

object EventOutboxMapper {
    fun toDomain(entity: EventOutboxEntity): EventOutbox {
        return EventOutbox(
            id = entity.id,
            eventId = entity.eventId,
            topic = entity.topic,
            partitionKey = entity.partitionKey,
            eventType = entity.eventType,
            payload = entity.payload,
            status = entity.status,
            retryCount = entity.retryCount,
            publishedAt = entity.publishedAt,
        )
    }

    fun toEntity(domain: EventOutbox): EventOutboxEntity {
        return EventOutboxEntity(
            eventId = domain.eventId,
            topic = domain.topic,
            partitionKey = domain.partitionKey,
            eventType = domain.eventType,
            payload = domain.payload,
            status = domain.status,
            retryCount = domain.retryCount,
            publishedAt = domain.publishedAt,
        )
    }
}
