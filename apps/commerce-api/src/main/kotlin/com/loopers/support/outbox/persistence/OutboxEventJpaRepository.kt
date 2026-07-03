package com.loopers.support.outbox.persistence

import com.loopers.support.outbox.OutboxEventStatus
import jakarta.persistence.LockModeType
import java.time.ZonedDateTime
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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
          )
          and e.type in :publishableTypes
        order by e.eventCreatedAt asc, e.id asc
        """,
    )
    fun findPublishableForUpdate(
        publishableTypes: Set<String>,
        pendingStatus: OutboxEventStatus,
        failedStatus: OutboxEventStatus,
        now: ZonedDateTime,
        pageable: Pageable,
    ): List<OutboxEventJpaEntity>
}
