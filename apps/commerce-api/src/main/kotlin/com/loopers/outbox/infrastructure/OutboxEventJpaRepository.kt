package com.loopers.outbox.infrastructure

import com.loopers.outbox.domain.OutboxEvent
import com.loopers.outbox.domain.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.ZonedDateTime

interface OutboxEventJpaRepository : JpaRepository<OutboxEvent, Long> {
    fun findByStatusOrderByIdAsc(status: OutboxStatus, pageable: Pageable): List<OutboxEvent>

    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent o set o.status = :status where o.id in :ids")
    fun updateStatusByIdIn(status: OutboxStatus, ids: List<Long>): Int

    @Modifying(clearAutomatically = true)
    @Query("update OutboxEvent o set o.retryCount = o.retryCount + 1 where o.id in :ids")
    fun incrementRetryCountByIdIn(ids: List<Long>): Int

    @Query("select o.id from OutboxEvent o where o.id in :ids and o.status = 'INIT' and o.retryCount >= :maxRetry")
    fun findRetryExhaustedIds(ids: List<Long>, maxRetry: Int): List<Long>

    fun deleteByStatusAndCreatedAtBefore(status: OutboxStatus, threshold: ZonedDateTime): Int
}
