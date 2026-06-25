package com.loopers.domain.payment.infrastructure.persistence

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.model.OutboxEventModel
import com.loopers.domain.payment.model.OutboxEventStatus
import com.loopers.domain.payment.model.OutboxEventType
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
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_events_type_status_created_at", columnList = "event_type, event_status, created_at"),
    ],
)
class OutboxEventJpaEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    var type: OutboxEventType,
    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long,
    @Lob
    @Column(name = "payload", nullable = false)
    var payload: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,
    @Column(name = "event_created_at", nullable = false)
    var eventCreatedAt: ZonedDateTime,
) : BaseEntity() {
    fun toDomain(): OutboxEventModel = OutboxEventModel(
        id = id,
        type = type,
        aggregateId = aggregateId,
        payload = payload,
        status = status,
        createdAt = eventCreatedAt,
    )

    companion object {
        fun fromDomain(event: OutboxEventModel): OutboxEventJpaEntity = OutboxEventJpaEntity(
            type = event.type,
            aggregateId = event.aggregateId,
            payload = event.payload,
            status = event.status,
            eventCreatedAt = event.createdAt,
        )
    }
}
