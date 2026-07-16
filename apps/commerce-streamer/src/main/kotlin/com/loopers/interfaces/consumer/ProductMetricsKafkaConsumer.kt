package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.config.kafka.KafkaConfig
import com.loopers.metrics.application.ConsumedEvent
import com.loopers.metrics.application.OrderCreatedEvent
import com.loopers.metrics.application.ProductEvent
import com.loopers.metrics.application.ProductMetricsService
import com.loopers.metrics.application.ProductViewedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ProductMetricsKafkaConsumer(
    private val productMetricsService: ProductMetricsService,
) {
    private val logger = LoggerFactory.getLogger(ProductMetricsKafkaConsumer::class.java)

    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)

    @KafkaListener(
        topics = ["product-events"],
        groupId = "commerce-streamer-metrics-product",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeProductEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            val event = parse<ProductEvent>(record) ?: return@forEach
            productMetricsService.handle(event, occurredAt(record))
        }
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["order-events"],
        groupId = "commerce-streamer-metrics-order",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeOrderEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            val event = parse<OrderCreatedEvent>(record) ?: return@forEach
            productMetricsService.handle(event, occurredAt(record))
        }
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["user-action-events"],
        groupId = "commerce-streamer-metrics-user-action",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeUserActionEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            val event = parse<ProductViewedEvent>(record) ?: return@forEach
            productMetricsService.handle(event, occurredAt(record))
        }
        acknowledgment.acknowledge()
    }

    private fun occurredAt(record: ConsumerRecord<String, ByteArray>): Instant =
        if (record.timestamp() >= 0) Instant.ofEpochMilli(record.timestamp()) else Instant.now()

    private inline fun <reified T : ConsumedEvent> parse(record: ConsumerRecord<String, ByteArray>): T? {
        val event = try {
            objectMapper.readValue(record.value(), T::class.java)
        } catch (e: InvalidTypeIdException) {
            logger.warn("알 수 없는 eventType — skip (topic={}, offset={}, type={})", record.topic(), record.offset(), e.typeId)
            return null
        } catch (e: Exception) {
            logger.warn("역직렬화 불가 메시지 — skip (topic={}, offset={}): {}", record.topic(), record.offset(), e.javaClass.simpleName)
            return null
        }
        if (event.eventId.isBlank()) {
            logger.warn("eventId 없는 메시지 — skip (topic={}, offset={})", record.topic(), record.offset())
            return null
        }
        return event
    }
}
