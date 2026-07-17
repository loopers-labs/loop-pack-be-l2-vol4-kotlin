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
    override fun claimPublishable(
        now: ZonedDateTime,
        claimExpiredBefore: ZonedDateTime,
        limit: Int,
    ): List<OutboxEventModel> {
        val events = outboxEventJpaRepository.findPublishableForUpdate(
            pendingStatus = OutboxEventStatus.PENDING,
            failedStatus = OutboxEventStatus.FAILED,
            publishingStatus = OutboxEventStatus.PUBLISHING,
            now = now,
            claimExpiredBefore = claimExpiredBefore,
            pageable = PageRequest.of(0, limit),
        )
        events.forEach { it.markPublishing(claimId = UUID.randomUUID(), claimedAt = now) }
        return outboxEventJpaRepository.saveAllAndFlush(events).map { it.toDomain() }
    }

    @Transactional
    override fun markPublished(
        eventId: UUID,
        claimId: UUID,
        publishedAt: ZonedDateTime,
    ): Boolean = outboxEventJpaRepository.markPublishedByClaim(
        eventId = eventId,
        claimId = claimId,
        publishingStatus = OutboxEventStatus.PUBLISHING,
        publishedStatus = OutboxEventStatus.PUBLISHED,
        publishedAt = publishedAt,
    ) == 1

    @Transactional
    override fun markFailed(
        eventId: UUID,
        claimId: UUID,
        error: String,
        nextRetryAt: ZonedDateTime,
        maxPublishAttempts: Int,
    ): Boolean = outboxEventJpaRepository.markFailedByClaim(
        eventId = eventId,
        claimId = claimId,
        publishingStatus = OutboxEventStatus.PUBLISHING,
        failedStatus = OutboxEventStatus.FAILED,
        deadStatus = OutboxEventStatus.DEAD,
        error = error,
        nextRetryAt = nextRetryAt,
        maxPublishAttempts = maxPublishAttempts,
    ) == 1

    @Transactional
    override fun markInternalProcessed(eventId: UUID, processedAt: ZonedDateTime): Boolean =
        outboxEventJpaRepository.markInternalProcessed(
            eventId = eventId,
            pendingStatus = OutboxEventStatus.PENDING,
            publishedStatus = OutboxEventStatus.PUBLISHED,
            processedAt = processedAt,
        ) == 1
}
