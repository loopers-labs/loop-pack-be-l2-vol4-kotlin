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
        publishableTypes: Set<String>,
        now: ZonedDateTime,
        limit: Int,
    ): List<OutboxEventModel> {
        val indexes = events
            .withIndex()
            .filter { (_, event) ->
                event.type in publishableTypes &&
                    (
                        event.status == OutboxEventStatus.PENDING ||
                            (event.status == OutboxEventStatus.FAILED && event.nextRetryAt?.let { it <= now } == true)
                    )
            }
            .take(limit)
            .map { it.index }
        indexes.forEach { index ->
            events[index] = events[index].copy(status = OutboxEventStatus.PUBLISHING)
        }
        return indexes.map { events[it] }
    }

    override fun markPublished(eventId: UUID, publishedAt: ZonedDateTime) {
        val index = events.indexOfFirst { it.eventId == eventId }
        if (index >= 0) {
            events[index] = events[index].copy(
                status = OutboxEventStatus.PUBLISHED,
                publishedAt = publishedAt,
                nextRetryAt = null,
                lastError = null,
            )
        }
    }

    override fun markFailed(eventId: UUID, error: String, nextRetryAt: ZonedDateTime) {
        val index = events.indexOfFirst { it.eventId == eventId }
        if (index >= 0) {
            events[index] = events[index].copy(
                status = OutboxEventStatus.FAILED,
                retryCount = events[index].retryCount + 1,
                nextRetryAt = nextRetryAt,
                lastError = error,
            )
        }
    }
}
