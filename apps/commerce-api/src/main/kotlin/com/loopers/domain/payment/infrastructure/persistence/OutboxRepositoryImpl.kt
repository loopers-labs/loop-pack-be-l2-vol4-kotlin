package com.loopers.domain.payment.infrastructure.persistence

import com.loopers.domain.payment.model.OutboxEventModel
import com.loopers.domain.payment.model.OutboxEventStatus
import com.loopers.domain.payment.model.OutboxEventType
import com.loopers.domain.payment.port.OutboxRepository
import org.springframework.stereotype.Component

@Component
class OutboxRepositoryImpl(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
) : OutboxRepository {
    override fun save(event: OutboxEventModel): OutboxEventModel =
        outboxEventJpaRepository.saveAndFlush(OutboxEventJpaEntity.fromDomain(event)).toDomain()

    override fun findPendingByType(type: OutboxEventType): List<OutboxEventModel> =
        outboxEventJpaRepository.findAllByTypeAndStatus(type, OutboxEventStatus.PENDING).map { it.toDomain() }

    override fun markProcessed(id: Long) {
        outboxEventJpaRepository.findById(id).ifPresent { entity ->
            entity.markProcessed()
            outboxEventJpaRepository.saveAndFlush(entity)
        }
    }
}
