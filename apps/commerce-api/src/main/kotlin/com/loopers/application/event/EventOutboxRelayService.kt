package com.loopers.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeExternalEventPublisher
import com.loopers.event.CatalogEventMessage
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class EventOutboxRelayService(
    private val eventOutboxRepository: EventOutboxRepository,
    private val publisher: ProductLikeExternalEventPublisher,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun relayPending(limit: Int): Int {
        val pendingEvents = eventOutboxRepository.findPending(limit)

        pendingEvents.forEach { eventOutbox ->
            runCatching {
                val message = objectMapper.readValue(eventOutbox.payload, CatalogEventMessage::class.java)
                publisher.publish(
                    topic = eventOutbox.topic,
                    partitionKey = eventOutbox.partitionKey,
                    message = message,
                )
                eventOutboxRepository.save(eventOutbox.published())
            }.onFailure {
                eventOutboxRepository.save(eventOutbox.failed())
            }
        }

        return pendingEvents.size
    }
}
