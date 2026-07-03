package com.loopers.domain.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "outbox_events",
    uniqueConstraints = [UniqueConstraint(name = "uk_outbox_event_id", columnNames = ["event_id"])],
    indexes = [Index(name = "idx_outbox_status_id", columnList = "status, id")],
)
class OutboxEvent(
    eventId: String,
    topic: String,
    partitionKey: String,
    payload: String,
) : BaseEntity() {
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    var eventId: String = eventId
        protected set

    @Column(name = "topic", nullable = false)
    var topic: String = topic
        protected set

    @Column(name = "partition_key", nullable = false)
    var partitionKey: String = partitionKey
        protected set

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    var payload: String = payload
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    @Column(name = "sent_at")
    var sentAt: ZonedDateTime? = null
        protected set

    fun markSent() {
        status = OutboxStatus.SENT
        sentAt = ZonedDateTime.now()
    }
}
