package com.loopers.infrastructure.event

import com.loopers.domain.like.event.ProductLikeExternalEventPublisher
import com.loopers.event.CatalogEventMessage
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaProductLikeExternalEventPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) : ProductLikeExternalEventPublisher {
    override fun publish(
        topic: String,
        partitionKey: String,
        message: CatalogEventMessage,
    ) {
        kafkaTemplate.send(topic, partitionKey, message).get()
    }
}
