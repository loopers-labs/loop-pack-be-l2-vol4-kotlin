package com.loopers.infrastructure.event.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.event.EventHandled
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(
    name = "event_handled",
    indexes = [
        Index(name = "uk_event_handled_event_id", columnList = "event_id", unique = true),
    ],
)
class EventHandledEntity(
    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    var eventId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String,

    @Column(name = "handled_at", nullable = false)
    var handledAt: ZonedDateTime,
) : BaseEntity() {
    fun toDomain(): EventHandled {
        return EventHandled(
            eventId = eventId,
            eventType = eventType,
            handledAt = handledAt,
        )
    }
}
