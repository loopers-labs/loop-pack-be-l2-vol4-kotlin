package com.loopers.metrics.infrastructure

import com.loopers.metrics.domain.EventSubscription
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.ZonedDateTime

@Entity
@Table(name = "event_handled")
@IdClass(EventHandledId::class)
class EventHandled(
    eventId: String,
    subscription: EventSubscription,
) {
    @Id
    @Column(name = "event_id", length = 36, updatable = false)
    val eventId: String = eventId

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription", length = 20, updatable = false)
    val subscription: EventSubscription = subscription

    @Column(name = "handled_at", nullable = false, updatable = false)
    val handledAt: ZonedDateTime = ZonedDateTime.now()
}

data class EventHandledId(
    val eventId: String = "",
    val subscription: EventSubscription = EventSubscription.METRICS,
) : Serializable
