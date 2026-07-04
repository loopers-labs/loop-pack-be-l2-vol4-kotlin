package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxModel
import com.loopers.domain.outbox.OutboxRepository
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.stereotype.Repository

@Repository
class OutboxRepositoryImpl(
    private val outboxJpaRepository: OutboxJpaRepository,
) : OutboxRepository {
    override fun save(outbox: OutboxModel): OutboxModel = outboxJpaRepository.save(outbox)

    override fun findAllByStatus(status: OutboxStatus): List<OutboxModel> =
        outboxJpaRepository.findAllByStatus(status)
}
