package com.loopers.infrastructure.event.repository

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.infrastructure.event.entity.EventHandledEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun exists(eventId: String): Boolean {
        return eventHandledJpaRepository.existsByEventId(eventId)
    }

    override fun save(eventHandled: EventHandled): EventHandled {
        return try {
            eventHandledJpaRepository.save(
                EventHandledEntity(
                    eventId = eventHandled.eventId,
                    eventType = eventHandled.eventType,
                    handledAt = eventHandled.handledAt,
                ),
            ).toDomain()
        } catch (e: DataIntegrityViolationException) {
            eventHandledJpaRepository.findByEventId(eventHandled.eventId)
                ?.toDomain()
                ?: throw e
        }
    }
}
