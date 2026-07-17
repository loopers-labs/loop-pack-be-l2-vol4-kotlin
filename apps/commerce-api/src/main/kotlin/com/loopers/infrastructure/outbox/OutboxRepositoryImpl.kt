package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxModel
import com.loopers.domain.outbox.OutboxRepository
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

/**
 * OutboxRepository JPA 구현체.
 */
@Repository
class OutboxRepositoryImpl(
    private val outboxJpaRepository: OutboxJpaRepository,
) : OutboxRepository {

    /** Outbox 레코드를 저장한다. */
    override fun save(outbox: OutboxModel): OutboxModel {
        return outboxJpaRepository.save(outbox)
    }

    /** PENDING 상태 이벤트를 생성일 오름차순으로 최대 [limit]개 조회한다. */
    override fun findPendingEvents(limit: Int): List<OutboxModel> {
        return outboxJpaRepository.findByStatusOrderByCreatedAtAsc(
            OutboxStatus.PENDING,
            PageRequest.of(0, limit),
        )
    }
}
