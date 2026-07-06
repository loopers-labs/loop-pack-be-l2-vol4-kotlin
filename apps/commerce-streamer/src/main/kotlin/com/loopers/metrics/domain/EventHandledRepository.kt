package com.loopers.metrics.domain

interface EventHandledRepository {
    fun exists(eventId: String): Boolean

    fun save(eventHandled: EventHandled): EventHandled
}
