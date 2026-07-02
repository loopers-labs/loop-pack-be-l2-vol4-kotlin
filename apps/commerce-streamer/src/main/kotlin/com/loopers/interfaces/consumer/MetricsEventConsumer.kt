package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.application.metrics.IncomingEvent
import com.loopers.application.metrics.MetricsEventProcessor
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class MetricsEventConsumer(
    private val metricsEventProcessor: MetricsEventProcessor,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        containerFactory = KafkaConfig.SINGLE_LISTENER_WITH_DLT,
        groupId = "metrics-consumer",
    )
    fun consume(record: ConsumerRecord<String, ByteArray>) {
        val eventId = record.headers().lastHeader("eventId")
            ?.value()?.let { String(it, Charsets.UTF_8) }
            ?: throw IllegalArgumentException("eventId header is required. topic=${record.topic()}, offset=${record.offset()}")

        val eventType = record.headers().lastHeader("eventType")
            ?.value()?.let { String(it, Charsets.UTF_8) }
            ?: throw IllegalArgumentException("eventType header is required. topic=${record.topic()}, offset=${record.offset()}")

        val payload: Map<String, Any> = objectMapper.readValue(String(record.value(), Charsets.UTF_8))

        metricsEventProcessor.process(
            IncomingEvent(
                eventId = eventId,
                eventType = eventType,
                payload = payload,
            ),
        )
    }
}
