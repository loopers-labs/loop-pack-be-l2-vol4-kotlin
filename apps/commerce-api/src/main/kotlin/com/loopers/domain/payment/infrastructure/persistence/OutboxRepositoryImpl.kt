package com.loopers.domain.payment.infrastructure.persistence

import com.loopers.domain.payment.model.OutboxEventModel
import com.loopers.domain.payment.port.OutboxRepository
import org.springframework.stereotype.Component

@Component
class OutboxRepositoryImpl(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
) : OutboxRepository {
    override fun save(event: OutboxEventModel): OutboxEventModel =
        outboxEventJpaRepository.saveAndFlush(OutboxEventJpaEntity.fromDomain(event)).toDomain()
}
