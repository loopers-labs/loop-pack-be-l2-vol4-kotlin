package com.loopers.infrastructure.event

import com.loopers.domain.event.EventHandledRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class EventHandledRepositoryImpl(
    private val eventHandledJpaRepository: EventHandledJpaRepository
) : EventHandledRepository {
    override fun insertIfAbsent(eventId: String, now: ZonedDateTime): Int = eventHandledJpaRepository.insertIfAbsent(eventId, now)
}
