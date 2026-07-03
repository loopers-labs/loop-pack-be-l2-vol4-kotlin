package com.loopers.application.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

// BEFORE_COMMIT: 원본 트랜잭션 안에서 outbox 행을 기록해 비즈니스 데이터와 원자적으로 커밋한다.
// (좋아요/결제성공 usecase 는 쓰기 트랜잭션이므로 flush 가 정상 동작한다. 조회(readOnly)는 대상 아님 — 직접 발행.)
@Component
class OutboxEventCaptureListener(
    private val factory: OutboxMessageFactory,
    private val outboxRepository: OutboxEventRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun capture(event: Any) {
        val draft = factory.from(event) ?: return
        outboxRepository.save(
            OutboxEvent(topic = draft.topic, partitionKey = draft.partitionKey, payload = draft.payload),
        )
    }
}
