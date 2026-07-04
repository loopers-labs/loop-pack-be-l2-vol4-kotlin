package com.loopers.domain.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "event_handled")
class EventHandledModel(
    @Id
    @Column(name = "event_id")
    val eventId: String,
    val handledAt: ZonedDateTime
)
