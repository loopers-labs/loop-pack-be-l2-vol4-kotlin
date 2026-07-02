package com.loopers.infrastructure.event.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.model.EventOutboxStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(
    name = "event_outbox",
    indexes = [
        Index(name = "uk_event_outbox_event_id", columnList = "event_id", unique = true),
        Index(name = "idx_event_outbox_status_id", columnList = "status, id"),
    ],
)
class EventOutboxEntity(
    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    var eventId: String,

    @Column(nullable = false, length = 100)
    var topic: String,

    @Column(name = "partition_key", nullable = false, length = 100)
    var partitionKey: String,

    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String,

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: EventOutboxStatus = EventOutboxStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null,
) : BaseEntity() {
    fun update(eventOutbox: EventOutbox) {
        topic = eventOutbox.topic
        partitionKey = eventOutbox.partitionKey
        eventType = eventOutbox.eventType
        payload = eventOutbox.payload
        status = eventOutbox.status
        retryCount = eventOutbox.retryCount
        publishedAt = eventOutbox.publishedAt
    }
}
