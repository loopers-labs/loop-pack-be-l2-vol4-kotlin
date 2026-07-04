package com.loopers.domain.event

import java.time.ZonedDateTime

interface EventHandledRepository {
    fun insertIfAbsent(eventId: String, now: ZonedDateTime): Int
}
