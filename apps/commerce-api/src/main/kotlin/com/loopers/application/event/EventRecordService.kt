package com.loopers.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.event.CatalogEventMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EventRecordService(
    private val eventOutboxRepository: EventOutboxRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${commerce.events.catalog-topic:catalog-events}")
    private val catalogTopic: String,
) {
    fun record(event: ProductLikeEvent.Like): EventOutbox {
        return saveCatalogMessage(ProductLikeExternalEventMessagePayload.from(event))
    }

    fun record(event: ProductLikeEvent.Unlike): EventOutbox {
        return saveCatalogMessage(ProductLikeExternalEventMessagePayload.from(event))
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
