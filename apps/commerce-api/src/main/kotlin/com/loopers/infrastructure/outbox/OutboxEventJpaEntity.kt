package com.loopers.infrastructure.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
    ],
)
class OutboxEventJpaEntity(
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    val eventId: String = UUID.randomUUID().toString(),

    @Column(name = "aggregate_type", nullable = false, length = 50)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,

    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,

    @Column(name = "topic", nullable = false, length = 100)
    val topic: String,

    @Column(name = "partition_key", nullable = false, length = 100)
    val partitionKey: String,

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @Column(name = "published_at")
    var publishedAt: ZonedDateTime? = null
        protected set

    fun markPublished() {
        status = OutboxStatus.PUBLISHED
        publishedAt = ZonedDateTime.now()
    }

    @PrePersist
    private fun prePersist() {
        createdAt = ZonedDateTime.now()
    }
}

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
}
