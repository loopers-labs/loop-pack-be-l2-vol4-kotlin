package com.loopers.infrastructure.event

import com.loopers.domain.event.Event
import org.springframework.data.jpa.repository.JpaRepository

interface EventJpaRepository : JpaRepository<Event, Long> {
    fun findByIdAndDeletedAtIsNull(eventId: Long): Event?
}
