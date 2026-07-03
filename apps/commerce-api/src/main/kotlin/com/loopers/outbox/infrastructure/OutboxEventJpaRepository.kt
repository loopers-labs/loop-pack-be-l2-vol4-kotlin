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

    fun deleteByStatusAndCreatedAtBefore(status: OutboxStatus, threshold: ZonedDateTime): Int
}
