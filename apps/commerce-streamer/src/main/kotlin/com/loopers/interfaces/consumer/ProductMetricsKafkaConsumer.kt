package com.loopers.interfaces.consumer

import com.loopers.config.StreamerKafkaConfig
import com.loopers.eventstore.application.EventStoreAppender
import com.loopers.metrics.application.ProductMetricsService
import com.loopers.shared.event.ConsumedEvent
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ProductMetricsKafkaConsumer(
    private val productMetricsService: ProductMetricsService,
    private val eventStoreAppender: EventStoreAppender,
) {
    @KafkaListener(
        topics = ["product-events"],
        groupId = "commerce-streamer-metrics-product",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_DLQ,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeProductEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        consumeEach(messages, ProductEvent::class.java) { productMetricsService.handle(it) }
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["order-events"],
        groupId = "commerce-streamer-metrics-order",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_DLQ,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeOrderEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        consumeEach(messages, OrderCreatedEvent::class.java) { productMetricsService.handle(it) }
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["user-action-events"],
        groupId = "commerce-streamer-metrics-user-action",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_DLQ,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeUserActionEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        consumeEach(messages, ProductViewedEvent::class.java) { productMetricsService.handle(it) }
        acknowledgment.acknowledge()
    }

    private fun <T : ConsumedEvent> consumeEach(
        messages: List<ConsumerRecord<String, ByteArray>>,
        type: Class<T>,
        handle: (T) -> Unit,
    ) {
        messages.forEachIndexed { index, record ->
            val event = ConsumedEventDeserializer.read(record, type) ?: return@forEachIndexed
            try {
                eventStoreAppender.append(event.eventId, record.topic(), record.value())
                handle(event)
            } catch (e: Exception) {
                throw BatchListenerFailedException("metrics 소비 실패 (eventId=${event.eventId})", e, index)
            }
        }
    }
}
