package com.loopers.projection.like.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ProcessedKafkaEventJpaId(
    @Column(name = "event_id", nullable = false, columnDefinition = "BINARY(16)")
    var eventId: UUID = UUID(0L, 0L),
    @Column(name = "consumer_group", nullable = false, length = 128)
    var consumerGroup: String = "",
) : Serializable

@Entity
@Table(
    name = "processed_kafka_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_processed_kafka_events_consumer_group_event_id",
            columnNames = ["consumer_group", "event_id"],
        ),
    ],
)
class ProcessedKafkaEventJpaEntity(
    @EmbeddedId
    var id: ProcessedKafkaEventJpaId,
    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String,
    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now(),
)
