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
    // BEFORE_COMMIT는 활성 쓰기 트랜잭션 안에서 발행돼야 동작한다.
    // 트랜잭션 밖에서 이벤트를 발행하면 Spring이 리스너를 조용히 무시해 outbox 행이 생성되지 않는다.
    // (현재 호출자인 LikeProductUsecase, SyncPaymentResultUsecase 는 모두 @Transactional.)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun capture(event: Any) {
        val draft = factory.from(event) ?: return
        outboxRepository.save(
            OutboxEvent(eventId = draft.eventId, topic = draft.topic, partitionKey = draft.partitionKey, payload = draft.payload),
        )
    }
}
