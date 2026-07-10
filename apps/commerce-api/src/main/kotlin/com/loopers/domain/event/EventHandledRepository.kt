package com.loopers.domain.event

interface EventHandledRepository {
    fun claim(eventId: String, eventType: String): Boolean
}
