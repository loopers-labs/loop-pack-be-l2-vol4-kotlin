package com.loopers.domain.payment.support

import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxEventStatus
import com.loopers.support.outbox.OutboxRepository
import java.time.ZonedDateTime
import java.util.UUID

class FakeOutboxRepository : OutboxRepository {
    val events: MutableList<OutboxEventModel> = mutableListOf()

    override fun save(event: OutboxEventModel): OutboxEventModel {
        val saved = event.copy(id = events.size + 1L)
        events.add(saved)
        return saved
    }

    override fun findByEventIdOrNull(eventId: UUID): OutboxEventModel? =
        events.find { it.eventId == eventId }

    override fun findPendingByType(type: String): List<OutboxEventModel> =
        events.filter { it.type == type && it.status == OutboxEventStatus.PENDING }

    override fun claimPublishable(
        now: ZonedDateTime,
        claimExpiredBefore: ZonedDateTime,
        limit: Int,
    ): List<OutboxEventModel> {
        val indexes = events
            .withIndex()
            .filter { (_, event) ->
                event.publishable &&
                    (
                        event.status == OutboxEventStatus.PENDING ||
                            (event.status == OutboxEventStatus.FAILED && event.nextRetryAt?.let { it <= now } == true) ||
                            (
                                event.status == OutboxEventStatus.PUBLISHING &&
                                    event.claimedAt?.let { it <= claimExpiredBefore } != false
                            )
                    )
            }
            .take(limit)
            .map { it.index }
        indexes.forEach { index ->
            events[index] = events[index].copy(
                status = OutboxEventStatus.PUBLISHING,
                claimId = UUID.randomUUID(),
                claimedAt = now,
            )
        }
        return indexes.map { events[it] }
    }

    override fun markPublished(eventId: UUID, claimId: UUID, publishedAt: ZonedDateTime): Boolean {
        val index = events.indexOfFirst {
            it.eventId == eventId && it.status == OutboxEventStatus.PUBLISHING && it.claimId == claimId
        }
        if (index >= 0) {
            events[index] = events[index].copy(
                status = OutboxEventStatus.PUBLISHED,
                publishedAt = publishedAt,
                nextRetryAt = null,
                lastError = null,
                claimId = null,
                claimedAt = null,
            )
        }
        return index >= 0
    }

    override fun markFailed(
        eventId: UUID,
        claimId: UUID,
        error: String,
        nextRetryAt: ZonedDateTime,
        maxPublishAttempts: Int,
    ): Boolean {
        val index = events.indexOfFirst {
            it.eventId == eventId && it.status == OutboxEventStatus.PUBLISHING && it.claimId == claimId
        }
        if (index >= 0) {
            val retryCount = events[index].retryCount + 1
            val exhausted = retryCount >= maxPublishAttempts
            events[index] = events[index].copy(
                status = if (exhausted) OutboxEventStatus.DEAD else OutboxEventStatus.FAILED,
                retryCount = retryCount,
                nextRetryAt = if (exhausted) null else nextRetryAt,
                lastError = error,
                claimId = null,
                claimedAt = null,
            )
        }
        return index >= 0
    }

    override fun markInternalProcessed(eventId: UUID, processedAt: ZonedDateTime): Boolean {
        val index = events.indexOfFirst {
            it.eventId == eventId && it.status == OutboxEventStatus.PENDING && !it.publishable
        }
        if (index >= 0) {
            events[index] = events[index].copy(
                status = OutboxEventStatus.PUBLISHED,
                publishedAt = processedAt,
            )
        }
        return index >= 0
    }
}
