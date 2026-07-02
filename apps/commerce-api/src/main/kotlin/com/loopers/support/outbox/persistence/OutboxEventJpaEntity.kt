package com.loopers.support.outbox.persistence

import com.loopers.domain.BaseEntity
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxEventStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_events_type_status_created_at", columnList = "event_type, event_status, created_at"),
        Index(name = "idx_outbox_events_status_next_retry_at", columnList = "event_status, next_retry_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_outbox_events_event_id", columnNames = ["event_id"]),
    ],
)
class OutboxEventJpaEntity(
    @Column(name = "event_id", nullable = false, updatable = false)
    var eventId: UUID,
    @Column(name = "event_type", nullable = false)
    var type: String,
    @Column(name = "aggregate_type", nullable = false)
    var aggregateType: String,
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long,
    @Lob
    @Column(name = "payload", nullable = false)
    var payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    @Column(name = "next_retry_at")
    var nextRetryAt: ZonedDateTime? = null,
    @Lob
    @Column(name = "last_error")
    var lastError: String? = null,
    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null,
    @Column(name = "event_created_at", nullable = false)
    var eventCreatedAt: ZonedDateTime,
) : BaseEntity() {
    fun markPublishing() {
        status = OutboxEventStatus.PUBLISHING
    }

    fun markPublished(publishedAt: ZonedDateTime) {
        status = OutboxEventStatus.PUBLISHED
        this.publishedAt = publishedAt
        nextRetryAt = null
        lastError = null
    }

    fun markFailed(error: String, nextRetryAt: ZonedDateTime) {
        status = OutboxEventStatus.FAILED
        retryCount += 1
        this.nextRetryAt = nextRetryAt
        lastError = error
    }

    fun toDomain(): OutboxEventModel = OutboxEventModel(
        id = id,
        eventId = eventId,
        type = type,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        payload = payload,
        status = status,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        lastError = lastError,
        publishedAt = publishedAt,
        createdAt = eventCreatedAt,
    )

    companion object {
        fun fromDomain(event: OutboxEventModel): OutboxEventJpaEntity = OutboxEventJpaEntity(
            eventId = event.eventId,
            type = event.type,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            payload = event.payload,
            status = event.status,
            retryCount = event.retryCount,
            nextRetryAt = event.nextRetryAt,
            lastError = event.lastError,
            publishedAt = event.publishedAt,
            eventCreatedAt = event.createdAt,
        )
    }
}
