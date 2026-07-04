package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxModel
import com.loopers.domain.outbox.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxJpaRepository : JpaRepository<OutboxModel, Long> {
    fun findAllByStatus(status: OutboxStatus): List<OutboxModel>
}
