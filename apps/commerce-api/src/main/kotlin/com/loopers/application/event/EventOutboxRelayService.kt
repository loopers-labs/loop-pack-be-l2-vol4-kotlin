package com.loopers.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.ExternalEventPublisher
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.event.CatalogEventMessage
import com.loopers.event.OrderEventMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class EventOutboxRelayService(
    private val eventOutboxRepository: EventOutboxRepository,
    private val publisher: ExternalEventPublisher,
    private val objectMapper: ObjectMapper,
    @Value("\${commerce.events.catalog-topic:catalog-events}")
    private val catalogTopic: String,
    @Value("\${commerce.events.order-topic:order-events}")
    private val orderTopic: String,
) {
    @Transactional
    fun relayPending(limit: Int): Int {
        val pendingEvents = eventOutboxRepository.findPending(limit)

        pendingEvents.forEach { eventOutbox ->
            runCatching {
                val message = deserialize(eventOutbox.topic, eventOutbox.payload)
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

    private fun deserialize(topic: String, payload: String): Any {
        return when (topic) {
            catalogTopic -> objectMapper.readValue(payload, CatalogEventMessage::class.java)
            orderTopic -> objectMapper.readValue(payload, OrderEventMessage::class.java)
            else -> throw IllegalArgumentException("Unsupported outbox topic: $topic")
        }
    }
}
