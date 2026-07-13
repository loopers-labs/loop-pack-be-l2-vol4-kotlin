package com.loopers.config.redis

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class RankingDatePolicy(
    private val properties: RankingRedisProperties,
) {
    fun dateOf(occurredAt: ZonedDateTime): LocalDate = LocalDate.MIN

    fun expiresAt(date: LocalDate): Instant = Instant.EPOCH

    fun isExpired(date: LocalDate, now: Instant): Boolean = false
}
