package com.loopers.infrastructure.event.repository

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.infrastructure.event.entity.EventHandledEntity
import org.springframework.stereotype.Component

@Component
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {
    override fun exists(
        consumerGroup: String,
        eventId: String,
    ): Boolean {
        return eventHandledJpaRepository.existsByConsumerGroupAndEventId(
            consumerGroup = consumerGroup,
            eventId = eventId,
        )
    }

    override fun save(eventHandled: EventHandled): EventHandled {
        return eventHandledJpaRepository.save(
            EventHandledEntity(
                consumerGroup = eventHandled.consumerGroup,
                eventId = eventHandled.eventId,
                eventType = eventHandled.eventType,
                handledAt = eventHandled.handledAt,
            ),
        ).toDomain()
    }
}
