package com.loopers.support.outbox.persistence

import com.loopers.support.outbox.OutboxEventStatus
import jakarta.persistence.LockModeType
import java.time.ZonedDateTime
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, Long> {
    fun findByEventId(eventId: UUID): OutboxEventJpaEntity?
    fun findAllByTypeAndStatus(type: String, status: OutboxEventStatus): List<OutboxEventJpaEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select e
        from OutboxEventJpaEntity e
        where (
            e.status = :pendingStatus
            or (e.status = :failedStatus and e.nextRetryAt <= :now)
            or (
                e.status = :publishingStatus
                and (e.claimedAt is null or e.claimedAt <= :claimExpiredBefore)
            )
          )
          and e.topicName is not null
          and e.partitionKey is not null
        order by e.eventCreatedAt asc, e.id asc
        """,
    )
    fun findPublishableForUpdate(
        pendingStatus: OutboxEventStatus,
        failedStatus: OutboxEventStatus,
        publishingStatus: OutboxEventStatus,
        now: ZonedDateTime,
        claimExpiredBefore: ZonedDateTime,
        pageable: Pageable,
    ): List<OutboxEventJpaEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OutboxEventJpaEntity e
        set e.status = :publishedStatus,
            e.publishedAt = :publishedAt,
            e.nextRetryAt = null,
            e.lastError = null,
            e.claimId = null,
            e.claimedAt = null
        where e.eventId = :eventId
          and e.status = :publishingStatus
          and e.claimId = :claimId
        """,
    )
    fun markPublishedByClaim(
        eventId: UUID,
        claimId: UUID,
        publishingStatus: OutboxEventStatus,
        publishedStatus: OutboxEventStatus,
        publishedAt: ZonedDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OutboxEventJpaEntity e
        set e.status = case
                when e.retryCount + 1 >= :maxPublishAttempts then :deadStatus
                else :failedStatus
            end,
            e.nextRetryAt = case
                when e.retryCount + 1 >= :maxPublishAttempts then null
                else :nextRetryAt
            end,
            e.retryCount = e.retryCount + 1,
            e.lastError = :error,
            e.claimId = null,
            e.claimedAt = null
        where e.eventId = :eventId
          and e.status = :publishingStatus
          and e.claimId = :claimId
        """,
    )
    fun markFailedByClaim(
        eventId: UUID,
        claimId: UUID,
        publishingStatus: OutboxEventStatus,
        failedStatus: OutboxEventStatus,
        deadStatus: OutboxEventStatus,
        error: String,
        nextRetryAt: ZonedDateTime,
        maxPublishAttempts: Int,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OutboxEventJpaEntity e
        set e.status = :publishedStatus,
            e.publishedAt = :processedAt,
            e.nextRetryAt = null,
            e.lastError = null
        where e.eventId = :eventId
          and e.status = :pendingStatus
          and e.topicName is null
          and e.partitionKey is null
        """,
    )
    fun markInternalProcessed(
        eventId: UUID,
        pendingStatus: OutboxEventStatus,
        publishedStatus: OutboxEventStatus,
        processedAt: ZonedDateTime,
    ): Int
}
