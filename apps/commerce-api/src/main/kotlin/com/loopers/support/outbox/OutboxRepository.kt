package com.loopers.support.outbox

import java.time.ZonedDateTime
import java.util.UUID

interface OutboxRepository {
    fun save(event: OutboxEventModel): OutboxEventModel
    fun findByEventIdOrNull(eventId: UUID): OutboxEventModel?
    fun findPendingByType(type: String): List<OutboxEventModel>
    fun claimPublishable(
        now: ZonedDateTime,
        claimExpiredBefore: ZonedDateTime,
        limit: Int,
    ): List<OutboxEventModel>
    fun markPublished(eventId: UUID, claimId: UUID, publishedAt: ZonedDateTime): Boolean
    fun markFailed(
        eventId: UUID,
        claimId: UUID,
        error: String,
        nextRetryAt: ZonedDateTime,
        maxPublishAttempts: Int,
    ): Boolean
    fun markInternalProcessed(eventId: UUID, processedAt: ZonedDateTime): Boolean
}
