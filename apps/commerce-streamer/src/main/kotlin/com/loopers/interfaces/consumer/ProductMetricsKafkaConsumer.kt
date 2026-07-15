package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.metrics.application.ProductMetricsService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ProductMetricsKafkaConsumer(
    private val productMetricsService: ProductMetricsService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(ProductMetricsKafkaConsumer::class.java)

    @KafkaListener(
        topics = ["catalog-events", "order-events", "user-action-events"],
        groupId = "commerce-streamer-metrics",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consume(messages: List<ConsumerRecord<Any, Any>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            val payload = parse(record) ?: return@forEach
            val eventId = payload["eventId"]?.asText()
            if (eventId.isNullOrBlank()) {
                logger.warn("eventId 없는 메시지 — skip (topic={}, offset={})", record.topic(), record.offset())
                return@forEach
            }
            productMetricsService.handle(eventId, payload["eventType"]?.asText().orEmpty(), payload, occurredAt(record))
        }
        acknowledgment.acknowledge()
    }

    private fun occurredAt(record: ConsumerRecord<Any, Any>): Instant =
        if (record.timestamp() >= 0) Instant.ofEpochMilli(record.timestamp()) else Instant.now()

    private fun parse(record: ConsumerRecord<Any, Any>): JsonNode? = try {
        objectMapper.readTree(record.value() as ByteArray)
    } catch (e: Exception) {
        logger.warn("파싱 불가 메시지 — skip (topic={}, offset={}): {}", record.topic(), record.offset(), e.javaClass.simpleName)
        null
    }
}
