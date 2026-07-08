package com.loopers.domain.eventhandled

interface EventHandledRepository {
    fun existsByEventId(eventId: String): Boolean
    fun save(model: EventHandledModel): EventHandledModel
}
