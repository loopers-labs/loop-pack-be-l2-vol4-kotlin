package com.loopers.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EventOutboxService(
    private val eventOutboxRepository: EventOutboxRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${commerce.events.catalog-topic:catalog-events}")
    private val catalogTopic: String,
) {
    fun record(event: ProductLikeEvent.Like): EventOutbox {
        val message = CatalogEventMessage(
            eventId = event.eventId,
            eventType = CatalogEventType.PRODUCT_LIKED,
            aggregateId = event.productId,
            productId = event.productId,
            brandId = event.brandId,
            memberId = event.memberId,
            version = event.version,
            occurredAt = event.occurredAt,
        )

        return saveCatalogMessage(message)
    }

    fun record(event: ProductLikeEvent.Unlike): EventOutbox {
        val message = CatalogEventMessage(
            eventId = event.eventId,
            eventType = CatalogEventType.PRODUCT_UNLIKED,
            aggregateId = event.productId,
            productId = event.productId,
            brandId = event.brandId,
            memberId = event.memberId,
            version = event.version,
            occurredAt = event.occurredAt,
        )

        return saveCatalogMessage(message)
    }

    private fun saveCatalogMessage(message: CatalogEventMessage): EventOutbox {
        return eventOutboxRepository.save(
            EventOutbox(
                eventId = message.eventId,
                topic = catalogTopic,
                partitionKey = message.productId.toString(),
                eventType = message.eventType.name,
                payload = objectMapper.writeValueAsString(message),
            ),
        )
    }
}
