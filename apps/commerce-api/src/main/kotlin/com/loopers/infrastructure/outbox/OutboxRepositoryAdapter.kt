package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.Outbox
import com.loopers.domain.outbox.OutboxRepositoryPort
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class OutboxRepositoryAdapter(
    private val outboxJpaRepository: OutboxJpaRepository,
) : OutboxRepositoryPort {
    override fun save(outbox: Outbox): Outbox =
        outboxJpaRepository.save(OutboxEntity.from(outbox)).toDomain()

    override fun markPublished(eventId: String) {
        outboxJpaRepository.findByEventId(eventId)?.let {
            it.markPublished()
            outboxJpaRepository.save(it)
        }
    }

    override fun findAllCreatedBefore(threshold: ZonedDateTime): List<Outbox> =
        outboxJpaRepository
            .findAllByStatusAndCreatedAtBefore(PersistedOutboxStatus.CREATED, threshold)
            .map { it.toDomain() }
}
