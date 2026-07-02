package com.loopers.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.ExternalEventPublisher
import com.loopers.domain.event.model.EventOutbox
import com.loopers.event.CatalogEventMessage
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaExternalEventPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) : ExternalEventPublisher {
    override fun publish(eventOutbox: EventOutbox) {
        val message = objectMapper.readValue(eventOutbox.payload, CatalogEventMessage::class.java)
        kafkaTemplate.send(eventOutbox.topic, eventOutbox.partitionKey, message).get()
    }
}
