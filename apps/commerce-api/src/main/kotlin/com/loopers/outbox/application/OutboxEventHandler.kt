package com.loopers.outbox.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.outbox.domain.OutboxEvent
import com.loopers.outbox.domain.OutboxEventRepository
import com.loopers.outbox.domain.OutboxPublishable
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * OutboxPublishable 이벤트를 발행 트랜잭션에 참여해 outbox 로 적재한다.
 * BEFORE_COMMIT + 어노테이션 없음 = 본 트랜잭션 참여 — 적재 실패 시 비즈니스도 함께 롤백(outbox 는 유실 불허).
 * @Async·REQUIRES_NEW 를 붙이면 별도 트랜잭션으로 빠져 원자성이 깨지므로 금지.
 */
@Component
class OutboxEventHandler(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun on(event: OutboxPublishable) {
        outboxEventRepository.save(
            OutboxEvent(
                aggregateType = event.aggregateType,
                aggregateId = event.aggregateId,
                eventType = event.eventType,
                payload = objectMapper.writeValueAsString(event),
            ),
        )
    }
}
