package com.loopers.domain.outbox

import java.time.ZonedDateTime

interface OutboxRepositoryPort {
    fun save(outbox: Outbox): Outbox
    fun markPublished(eventId: String)
    fun findAllCreatedBefore(threshold: ZonedDateTime): List<Outbox>
}
