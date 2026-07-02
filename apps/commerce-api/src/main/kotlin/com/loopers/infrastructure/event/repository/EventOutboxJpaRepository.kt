package com.loopers.infrastructure.event.repository

import com.loopers.domain.event.model.EventOutboxStatus
import com.loopers.infrastructure.event.entity.EventOutboxEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EventOutboxJpaRepository : JpaRepository<EventOutboxEntity, Long> {
    fun findAllByStatusOrderByIdAsc(status: EventOutboxStatus, pageable: Pageable): List<EventOutboxEntity>
}
