package com.loopers.infrastructure.eventhandled

import com.loopers.domain.eventhandled.EventHandledModel
import com.loopers.domain.eventhandled.EventHandledRepository
import org.springframework.stereotype.Repository

@Repository
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun existsByEventId(eventId: String): Boolean = eventHandledJpaRepository.existsByEventId(eventId)

    override fun save(model: EventHandledModel): EventHandledModel = eventHandledJpaRepository.save(model)
}
