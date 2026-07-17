package com.loopers.metrics.infrastructure

import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.EventSubscription
import org.springframework.stereotype.Repository

@Repository
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun exists(eventId: String, subscription: EventSubscription): Boolean =
        eventHandledJpaRepository.existsById(EventHandledId(eventId, subscription))

    override fun markHandled(eventId: String, subscription: EventSubscription) {
        eventHandledJpaRepository.save(EventHandled(eventId, subscription))
    }
}
