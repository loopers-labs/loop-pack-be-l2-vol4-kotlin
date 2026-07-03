package com.loopers.outbox.infrastructure

import com.loopers.outbox.domain.OutboxEvent
import com.loopers.outbox.domain.OutboxEventRepository
import com.loopers.outbox.domain.OutboxStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Repository
class OutboxEventRepositoryImpl(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
) : OutboxEventRepository {
    override fun save(outboxEvent: OutboxEvent): OutboxEvent =
        outboxEventJpaRepository.save(outboxEvent)

    override fun findPending(limit: Int): List<OutboxEvent> =
        outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.INIT, PageRequest.of(0, limit))

    @Transactional
    override fun markSent(ids: List<Long>): Int =
        outboxEventJpaRepository.updateStatusByIdIn(OutboxStatus.SENT, ids)

    @Transactional
    override fun deleteSentBefore(threshold: ZonedDateTime): Int =
        outboxEventJpaRepository.deleteByStatusAndCreatedAtBefore(OutboxStatus.SENT, threshold)
}
