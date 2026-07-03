package com.loopers.infrastructure.event

import com.loopers.domain.event.Event
import com.loopers.domain.event.EventRepository
import org.springframework.stereotype.Component

@Component
class EventRepositoryImpl(
    private val eventJpaRepository: EventJpaRepository,
) : EventRepository {
    override fun save(event: Event): Event = eventJpaRepository.save(event)

    override fun findById(eventId: Long): Event? =
        eventJpaRepository.findByIdAndDeletedAtIsNull(eventId)
}
