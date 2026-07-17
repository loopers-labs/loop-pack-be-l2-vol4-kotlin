package com.loopers.interfaces.consumer

import com.loopers.config.StreamerKafkaConfig
import com.loopers.ranking.application.FallbackItem
import com.loopers.ranking.application.RankingFallbackService
import com.loopers.ranking.domain.RankingWeights
import com.loopers.shared.event.ConsumedEvent
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RankingFallbackKafkaConsumer(
    private val rankingFallbackService: RankingFallbackService,
) {
    @KafkaListener(
        topics = ["product-events"],
        groupId = "commerce-streamer-ranking-fallback-product",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_FALLBACK,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeProductEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        applyThenAck(messages, ProductEvent::class.java, acknowledgment)
    }

    @KafkaListener(
        topics = ["order-events"],
        groupId = "commerce-streamer-ranking-fallback-order",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_FALLBACK,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeOrderEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        applyThenAck(messages, OrderCreatedEvent::class.java, acknowledgment)
    }

    @KafkaListener(
        topics = ["user-action-events"],
        groupId = "commerce-streamer-ranking-fallback-user-action",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_FALLBACK,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeUserActionEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        applyThenAck(messages, ProductViewedEvent::class.java, acknowledgment)
    }

    private fun <T : ConsumedEvent> applyThenAck(
        messages: List<ConsumerRecord<String, ByteArray>>,
        type: Class<T>,
        acknowledgment: Acknowledgment,
    ) {
        val items = messages.mapNotNull { record ->
            val event = ConsumedEventDeserializer.read(record, type) ?: return@mapNotNull null
            FallbackItem(event.eventId, occurredAt(record), RankingWeights.changesOf(event))
        }
        rankingFallbackService.applyBatch(items)
        acknowledgment.acknowledge()
    }

    private fun occurredAt(record: ConsumerRecord<String, ByteArray>): Instant =
        if (record.timestamp() >= 0) Instant.ofEpochMilli(record.timestamp()) else Instant.now()
}
