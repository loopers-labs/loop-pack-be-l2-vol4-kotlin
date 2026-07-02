package com.loopers.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, Long> {
    fun findTop100ByStatusOrderByCreatedAtAsc(status: OutboxStatus): List<OutboxEventJpaEntity>
}
