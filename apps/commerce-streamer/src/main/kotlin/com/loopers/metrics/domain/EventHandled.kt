package com.loopers.metrics.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "event_handled")
class EventHandled(
    eventId: String,
) {
    @Id
    @Column(name = "event_id", length = 36, updatable = false)
    val eventId: String = eventId

    @Column(name = "handled_at", nullable = false, updatable = false)
    val handledAt: ZonedDateTime = ZonedDateTime.now()
}
