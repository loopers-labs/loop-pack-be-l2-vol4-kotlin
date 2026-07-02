package com.loopers.support.outbox.persistence

import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxEventStatus
import com.loopers.support.outbox.OutboxRepository
import java.time.ZonedDateTime
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxRepositoryImpl(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
) : OutboxRepository {
    @Transactional
    override fun save(event: OutboxEventModel): OutboxEventModel =
        outboxEventJpaRepository.saveAndFlush(OutboxEventJpaEntity.fromDomain(event)).toDomain()

    @Transactional(readOnly = true)
    override fun findByEventIdOrNull(eventId: UUID): OutboxEventModel? =
        outboxEventJpaRepository.findByEventId(eventId)?.toDomain()

    @Transactional(readOnly = true)
    override fun findPendingByType(type: String): List<OutboxEventModel> =
        outboxEventJpaRepository.findAllByTypeAndStatus(type, OutboxEventStatus.PENDING).map { it.toDomain() }

    @Transactional
    override fun claimPublishable(type: String, now: ZonedDateTime, limit: Int): List<OutboxEventModel> {
        val events = outboxEventJpaRepository.findPublishableForUpdate(
            type = type,
            pendingStatus = OutboxEventStatus.PENDING,
            failedStatus = OutboxEventStatus.FAILED,
            now = now,
            pageable = PageRequest.of(0, limit),
        )
        events.forEach { it.markPublishing() }
        return outboxEventJpaRepository.saveAllAndFlush(events).map { it.toDomain() }
    }

    @Transactional
    override fun markPublished(eventId: UUID, publishedAt: ZonedDateTime) {
        outboxEventJpaRepository.findByEventId(eventId)?.let { entity ->
            entity.markPublished(publishedAt)
            outboxEventJpaRepository.saveAndFlush(entity)
        }
    }

    @Transactional
    override fun markFailed(eventId: UUID, error: String, nextRetryAt: ZonedDateTime) {
        outboxEventJpaRepository.findByEventId(eventId)?.let { entity ->
            entity.markFailed(error, nextRetryAt)
            outboxEventJpaRepository.saveAndFlush(entity)
        }
    }
}
