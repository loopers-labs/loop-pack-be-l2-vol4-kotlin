package com.loopers.interfaces.consumer.ranking

import com.loopers.application.ranking.RankingEventService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.event.NonRetryableEventException
import com.loopers.event.OrderEventMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class OrderRankingEventConsumer(
    private val service: RankingEventService,
    private val properties: RankingRedisProperties,
) {
    @KafkaListener(
        topics = ["\${commerce.events.order-topic:order-events}"],
        groupId = "\${commerce.ranking.consumer-group:commerce-ranking}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun receive(
        messages: List<OrderEventMessage>,
        acknowledgment: Acknowledgment,
    ) {
        messages.forEachChunked { index, message ->
            try {
                service.handle(message)
            } catch (exception: NonRetryableEventException) {
                throw BatchListenerFailedException(exception.message ?: "Invalid order ranking event", exception, index)
            }
        }
        acknowledgment.acknowledge()
    }

    private fun <T> List<T>.forEachChunked(action: (Int, T) -> Unit) {
        chunked(properties.eventChunkSize).forEachIndexed { chunkIndex, chunk ->
            chunk.forEachIndexed { itemIndex, item ->
                action(chunkIndex * properties.eventChunkSize + itemIndex, item)
            }
        }
    }
}
