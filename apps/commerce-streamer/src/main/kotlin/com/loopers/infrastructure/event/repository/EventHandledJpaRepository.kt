package com.loopers.infrastructure.event.repository

import com.loopers.infrastructure.event.entity.EventHandledEntity
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandledEntity, Long> {
    fun findByEventId(eventId: String): EventHandledEntity?

    fun existsByEventId(eventId: String): Boolean
}
