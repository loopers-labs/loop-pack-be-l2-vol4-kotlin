package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEventModel
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxEventJpaRepository : JpaRepository<OutboxEventModel, Long> {
    fun findByStatusOrderByIdAsc(status: OutboxStatus, pageable: Pageable): List<OutboxEventModel>

    @Modifying
    @Query("UPDATE OutboxEventModel o SET o.status = :status WHERE o.id IN :ids")
    fun updateStatusByIdIn(
        @Param("status") status: OutboxStatus,
        @Param("ids") ids: List<Long>,
    ): Int
}
