package com.loopers.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, Long> {
    fun findTop100ByStatusOrderByCreatedAtAsc(status: OutboxStatus): List<OutboxEventJpaEntity>

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OutboxEventJpaEntity event
        set event.status = :targetStatus
        where event.id = :id
          and event.status = :currentStatus
        """,
    )
    fun updateStatusIfCurrent(
        @Param("id") id: Long,
        @Param("currentStatus") currentStatus: OutboxStatus,
        @Param("targetStatus") targetStatus: OutboxStatus,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OutboxEventJpaEntity event
        set event.status = :targetStatus,
            event.publishedAt = :publishedAt
        where event.id = :id
          and event.status = :currentStatus
        """,
    )
    fun updateStatusAndPublishedAtIfCurrent(
        @Param("id") id: Long,
        @Param("currentStatus") currentStatus: OutboxStatus,
        @Param("targetStatus") targetStatus: OutboxStatus,
        @Param("publishedAt") publishedAt: ZonedDateTime,
    ): Int
}
