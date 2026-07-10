package com.loopers.domain.metrics

interface EventHandledRepository {
    fun markHandled(eventId: String): Boolean
}
