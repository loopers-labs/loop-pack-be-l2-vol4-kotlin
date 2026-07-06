package com.loopers.metrics.infrastructure

import com.loopers.metrics.domain.EventHandled
import com.loopers.metrics.domain.EventHandledRepository
import org.springframework.stereotype.Repository

@Repository
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun exists(eventId: String): Boolean =
        eventHandledJpaRepository.existsById(eventId)

    override fun save(eventHandled: EventHandled): EventHandled =
        eventHandledJpaRepository.save(eventHandled)
}
