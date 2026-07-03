package com.loopers.infrastructure.eventhandled

import com.loopers.domain.event.EventHandledRepository
import org.springframework.stereotype.Component

@Component
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun claim(eventId: String, eventType: String): Boolean {
        return eventHandledJpaRepository.insertIgnore(eventId = eventId, eventType = eventType) == 1
    }
}
