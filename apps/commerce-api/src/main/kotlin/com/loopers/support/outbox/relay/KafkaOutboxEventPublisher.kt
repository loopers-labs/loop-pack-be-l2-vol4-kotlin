package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class KafkaOutboxEventPublisher(
    @Qualifier("kafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val properties: OutboxRelayProperties,
) : OutboxEventPublisher {
    override fun publish(event: OutboxEventModel) {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "Outbox event publish must run outside an active transaction."
        }
        val topicName = requireNotNull(event.topicName) { "Publishable outbox topicName is required." }
        val partitionKey = requireNotNull(event.partitionKey) { "Publishable outbox partitionKey is required." }
        kafkaTemplate
            .send(topicName, partitionKey, event.toKafkaMessage())
            .get(properties.relayPublishTimeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun OutboxEventModel.toKafkaMessage(): OutboxEventKafkaMessage =
        OutboxEventKafkaMessage(
            eventId = eventId,
            eventType = type,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            payload = payload,
            createdAt = createdAt.toString(),
        )
}
