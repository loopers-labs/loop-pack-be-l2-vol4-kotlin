package com.loopers.outbox.infrastructure

import com.loopers.outbox.domain.EventMessagePublisher
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class KafkaEventMessagePublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) : EventMessagePublisher {
    @CircuitBreaker(name = "kafka-relay")
    override fun publish(topic: String, partitionKey: String, message: Any) {
        kafkaTemplate.send(topic, partitionKey, message).get(10, TimeUnit.SECONDS)
    }
}
