package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class OutboxEventRepositoryImpl(
    private val jpa: OutboxEventJpaRepository,
) : OutboxEventRepository {
    override fun save(event: OutboxEvent): OutboxEvent = jpa.save(event)

    override fun findTopPending(limit: Int): List<OutboxEvent> =
        jpa.findByStatusOrderByIdAsc(OutboxStatus.PENDING, PageRequest.of(0, limit))
}
