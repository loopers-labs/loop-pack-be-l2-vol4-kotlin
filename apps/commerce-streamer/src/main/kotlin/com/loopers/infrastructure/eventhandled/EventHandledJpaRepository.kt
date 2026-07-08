package com.loopers.infrastructure.eventhandled

import com.loopers.domain.eventhandled.EventHandledModel
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandledModel, String> {
    fun existsByEventId(eventId: String): Boolean
}
