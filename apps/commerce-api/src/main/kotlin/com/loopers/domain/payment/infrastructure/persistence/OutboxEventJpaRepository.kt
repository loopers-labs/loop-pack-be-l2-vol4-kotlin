package com.loopers.domain.payment.infrastructure.persistence

import com.loopers.domain.payment.model.OutboxEventStatus
import com.loopers.domain.payment.model.OutboxEventType
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, Long> {
    fun findAllByTypeAndStatus(type: OutboxEventType, status: OutboxEventStatus): List<OutboxEventJpaEntity>
}
