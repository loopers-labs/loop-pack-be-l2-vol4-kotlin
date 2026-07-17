package com.loopers.interfaces.consumer

import com.loopers.config.StreamerKafkaConfig
import com.loopers.ranking.application.RankingAccumulateService
import com.loopers.ranking.domain.RankingWeights
import com.loopers.shared.event.ConsumedEvent
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
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
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_DLQ,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeProductEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        consumeEach(messages, ProductEvent::class.java)
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["order-events"],
        groupId = "commerce-streamer-ranking-order",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_DLQ,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeOrderEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        consumeEach(messages, OrderCreatedEvent::class.java)
        acknowledgment.acknowledge()
    }

    @KafkaListener(
        topics = ["user-action-events"],
        groupId = "commerce-streamer-ranking-user-action",
        containerFactory = StreamerKafkaConfig.BATCH_LISTENER_DLQ,
        autoStartup = "\${kafka.listener.auto-startup:true}",
    )
    fun consumeUserActionEvents(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        consumeEach(messages, ProductViewedEvent::class.java)
        acknowledgment.acknowledge()
    }

    private fun <T : ConsumedEvent> consumeEach(messages: List<ConsumerRecord<String, ByteArray>>, type: Class<T>) {
        messages.forEachIndexed { index, record ->
            val event = ConsumedEventDeserializer.read(record, type) ?: return@forEachIndexed
            try {
                rankingAccumulateService.accumulate(event.eventId, occurredAt(record), RankingWeights.changesOf(event))
            } catch (e: Exception) {
                throw BatchListenerFailedException("랭킹 소비 실패 (eventId=${event.eventId})", e, index)
            }
        }
    }

    private fun occurredAt(record: ConsumerRecord<String, ByteArray>): Instant =
        if (record.timestamp() >= 0) Instant.ofEpochMilli(record.timestamp()) else Instant.now()
}
