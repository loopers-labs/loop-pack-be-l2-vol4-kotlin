package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxEventRepository
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class OutboxEventRepositoryImpl(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
) : OutboxEventRepository {
    override fun save(event: OutboxEventModel): OutboxEventModel = outboxEventJpaRepository.save(event)

    override fun findPendingBatch(limit: Int): List<OutboxEventModel> =
        outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, PageRequest.of(0, limit))

    @Transactional
    override fun markPublished(ids: List<Long>) {
        if (ids.isEmpty()) return
        outboxEventJpaRepository.updateStatusByIdIn(OutboxStatus.PUBLISHED, ids)
    }
}
