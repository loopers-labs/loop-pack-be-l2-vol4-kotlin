package com.loopers.eventstore.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(
    name = "event_store",
    indexes = [Index(name = "idx_event_store_topic_recorded", columnList = "topic, recorded_at")],
)
class EventStoreRecord(
    eventId: String,
    topic: String,
    payload: String,
) {
    @Id
    @Column(name = "event_id", length = 36, updatable = false)
    val eventId: String = eventId

    @Column(name = "topic", nullable = false, length = 60, updatable = false)
    val topic: String = topic

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    val payload: String = payload

    @Column(name = "recorded_at", nullable = false, updatable = false)
    val recordedAt: ZonedDateTime = ZonedDateTime.now()
}
