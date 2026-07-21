package com.loopers.domain.event

interface EventHandledRepository {
    fun exists(
        consumerGroup: String,
        eventId: String,
    ): Boolean

    fun save(eventHandled: EventHandled): EventHandled
}
