package com.loopers.support.outbox

import java.time.ZonedDateTime
import java.util.UUID

interface OutboxRepository {
    fun save(event: OutboxEventModel): OutboxEventModel
    fun findByEventIdOrNull(eventId: UUID): OutboxEventModel?
    fun findPendingByType(type: String): List<OutboxEventModel>
    fun claimPublishable(type: String, now: ZonedDateTime, limit: Int): List<OutboxEventModel>
    fun markPublished(eventId: UUID, publishedAt: ZonedDateTime)
    fun markFailed(eventId: UUID, error: String, nextRetryAt: ZonedDateTime)
}
