package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import com.loopers.ranking.application.RankingAccumulateService
import com.loopers.ranking.application.ScoreChange
import com.loopers.ranking.domain.RankingWeights
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ProductRankingKafkaConsumer(
    private val rankingAccumulateService: RankingAccumulateService,
) {
    @KafkaListener(
        topics = ["product-events"],
        groupId = "commerce-streamer-ranking-product",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeProductEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            val event = ConsumedEventDeserializer.read(record, ProductEvent::class.java) ?: return@forEach
            val amount = when (event) {
                is ProductEvent.Liked -> RankingWeights.LIKE
                is ProductEvent.Unliked -> RankingWeights.LIKE.negate()
            }
            rankingAccumulateService.accumulate(event.eventId, occurredAt(record), listOf(ScoreChange(event.productId, amount)))
        }
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["order-events"],
        groupId = "commerce-streamer-ranking-order",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeOrderEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            val event = ConsumedEventDeserializer.read(record, OrderCreatedEvent::class.java) ?: return@forEach
            val changes = event.items.map { line -> ScoreChange(line.productId, RankingWeights.ORDER_LINE) }
            rankingAccumulateService.accumulate(event.eventId, occurredAt(record), changes)
        }
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["user-action-events"],
        groupId = "commerce-streamer-ranking-user-action",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeUserActionEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            val event = ConsumedEventDeserializer.read(record, ProductViewedEvent::class.java) ?: return@forEach
            rankingAccumulateService.accumulate(
                event.eventId,
                occurredAt(record),
                listOf(ScoreChange(event.productId, RankingWeights.VIEW)),
            )
        }
        acknowledgment.acknowledge()
    }

    private fun occurredAt(record: ConsumerRecord<String, ByteArray>): Instant =
        if (record.timestamp() >= 0) Instant.ofEpochMilli(record.timestamp()) else Instant.now()
}
