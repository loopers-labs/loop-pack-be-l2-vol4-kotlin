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
        Index(
            name = "idx_outbox_events_status_next_retry_event_created",
            columnList = "event_status, next_retry_at, event_created_at, id",
        ),
        Index(
            name = "idx_outbox_events_status_claimed_event_created",
            columnList = "event_status, claimed_at, event_created_at, id",
        ),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_outbox_events_event_id", columnNames = ["event_id"]),
    ],
)
class OutboxEventJpaEntity(
    @Column(name = "event_id", nullable = false, updatable = false)
    var eventId: UUID,
    @Column(name = "event_type", nullable = false, updatable = false)
    var type: String,
    @Column(name = "aggregate_type", nullable = false, updatable = false)
    var aggregateType: String,
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    var aggregateId: Long,
    @Column(name = "topic_name", updatable = false)
    var topicName: String? = null,
    @Column(name = "partition_key", updatable = false)
    var partitionKey: String? = null,
    @Lob
    @Column(name = "payload", nullable = false, updatable = false)
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
    @Column(name = "claim_id")
    var claimId: UUID? = null,
    @Column(name = "claimed_at")
    var claimedAt: ZonedDateTime? = null,
    @Column(name = "event_created_at", nullable = false, updatable = false)
    var eventCreatedAt: ZonedDateTime,
) : BaseEntity() {
    fun markPublishing(claimId: UUID, claimedAt: ZonedDateTime) {
        status = OutboxEventStatus.PUBLISHING
        this.claimId = claimId
        this.claimedAt = claimedAt
    }

    fun toDomain(): OutboxEventModel = OutboxEventModel(
        id = id,
        eventId = eventId,
        type = type,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        topicName = topicName,
        partitionKey = partitionKey,
        payload = payload,
        status = status,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        lastError = lastError,
        publishedAt = publishedAt,
        claimId = claimId,
        claimedAt = claimedAt,
        createdAt = eventCreatedAt,
    )

    companion object {
        fun fromDomain(event: OutboxEventModel): OutboxEventJpaEntity = OutboxEventJpaEntity(
            eventId = event.eventId,
            type = event.type,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            topicName = event.topicName,
            partitionKey = event.partitionKey,
            payload = event.payload,
            status = event.status,
            retryCount = event.retryCount,
            nextRetryAt = event.nextRetryAt,
            lastError = event.lastError,
            publishedAt = event.publishedAt,
            claimId = event.claimId,
            claimedAt = event.claimedAt,
            eventCreatedAt = event.createdAt,
        )
    }
}
