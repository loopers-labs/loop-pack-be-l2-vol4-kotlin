package com.loopers.config.redis

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class RankingDatePolicy(
    private val properties: RankingRedisProperties,
) {
    fun dateOf(occurredAt: ZonedDateTime): LocalDate {
        return occurredAt.withZoneSameInstant(properties.zoneId).toLocalDate()
    }

    fun expiresAt(date: LocalDate): Instant {
        return date.plusDays(2).atStartOfDay(properties.zoneId).toInstant()
    }

    fun isExpired(date: LocalDate, now: Instant): Boolean {
        return !now.isBefore(expiresAt(date))
    }
}
