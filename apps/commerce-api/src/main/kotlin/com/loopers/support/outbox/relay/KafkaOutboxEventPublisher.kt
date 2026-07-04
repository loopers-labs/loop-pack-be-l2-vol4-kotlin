package com.loopers.support.outbox.relay

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.support.outbox.event.OutboxEventRouting
import java.util.concurrent.TimeUnit
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class KafkaOutboxEventPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
    private val properties: OutboxRelayProperties,
) : OutboxEventPublisher {
    override fun publish(event: OutboxEventModel) {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "Outbox event publish must run outside an active transaction."
        }
        val route = OutboxEventRouting.route(event)
        val message = event.toKafkaMessage()
        kafkaTemplate
            .send(route.topicName, route.key, message)
            .get(properties.relayPublishTimeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun OutboxEventModel.toKafkaMessage(): Any =
        when (type) {
            CommerceOutboxEventType.LIKE_COUNT_CHANGED_V1.name -> toLikeCountChangedKafkaMessage()
            else -> OutboxEventKafkaMessage(
                eventId = eventId,
                eventType = type,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                payload = payload,
                createdAt = createdAt.toString(),
            )
        }

    private fun OutboxEventModel.toLikeCountChangedKafkaMessage(): LikeCountChangedKafkaMessage {
        val payload = objectMapper.readValue(payload, LikeCountChangedPayload::class.java)
        return LikeCountChangedKafkaMessage(
            eventId = eventId,
            eventType = type,
            productId = requireNotNull(payload.productId) { "Outbox payload productId is required." },
            userId = requireNotNull(payload.userId) { "Outbox payload userId is required." },
            delta = requireNotNull(payload.delta) { "Outbox payload delta is required." },
        )
    }
}
