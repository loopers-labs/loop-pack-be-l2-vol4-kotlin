package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.event.IntegrationEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OutboxEventPublisher(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: IntegrationEvent) {
        outboxEventJpaRepository.save(createOutboxEvent(event))
    }

    private fun createOutboxEvent(event: IntegrationEvent): OutboxEventJpaEntity = OutboxEventJpaEntity(
        aggregateType = event.aggregateType,
        aggregateId = event.aggregateId,
        eventType = event.eventType,
        topic = event.topic,
        partitionKey = event.partitionKey,
        payload = objectMapper.writeValueAsString(event),
    )
}
