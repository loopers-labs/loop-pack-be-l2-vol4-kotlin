package com.loopers.domain.event

interface EventHandledRepository {
    fun exists(eventId: String): Boolean

    fun save(eventHandled: EventHandled): EventHandled
}
