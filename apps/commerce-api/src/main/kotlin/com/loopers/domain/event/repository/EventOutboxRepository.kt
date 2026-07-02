package com.loopers.domain.event.repository

import com.loopers.domain.event.model.EventOutbox

interface EventOutboxRepository {
    fun save(eventOutbox: EventOutbox): EventOutbox

    fun findPending(limit: Int): List<EventOutbox>
}
