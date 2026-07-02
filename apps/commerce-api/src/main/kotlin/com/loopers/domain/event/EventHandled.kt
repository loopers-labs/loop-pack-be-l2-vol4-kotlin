package com.loopers.domain.event

import java.time.ZonedDateTime

class EventHandled(
    val eventId: String,
    val eventType: String,
    val handledAt: ZonedDateTime = ZonedDateTime.now(),
)
