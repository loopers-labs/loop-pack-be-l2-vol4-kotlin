package com.loopers.metrics.domain

interface EventHandledRepository {
    fun exists(eventId: String, subscription: EventSubscription): Boolean

    fun markHandled(eventId: String, subscription: EventSubscription)
}
