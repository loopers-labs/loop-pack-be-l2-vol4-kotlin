package com.loopers.domain.event

interface EventRepository {
    fun save(event: Event): Event

    fun findById(eventId: Long): Event?
}
