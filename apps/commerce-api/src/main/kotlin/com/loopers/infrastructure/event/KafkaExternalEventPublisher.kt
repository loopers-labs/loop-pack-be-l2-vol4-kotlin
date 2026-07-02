package com.loopers.infrastructure.event

import com.loopers.domain.event.ExternalEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaExternalEventPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) : ExternalEventPublisher {
    override fun publish(topic: String, partitionKey: String, message: Any) {
        kafkaTemplate.send(topic, partitionKey, message).get()
    }
}
