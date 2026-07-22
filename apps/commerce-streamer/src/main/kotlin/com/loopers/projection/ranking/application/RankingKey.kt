package com.loopers.projection.ranking.application

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object RankingKey {
    const val TTL_SECONDS = 172_800L

    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    private const val DAILY_KEY_PREFIX = "ranking:all:"
    private const val DEDUP_KEY_PREFIX = "ranking:dedup:"
    private const val CARRY_OVER_MARKER_KEY_PREFIX = "ranking:carryover:"
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun daily(date: LocalDate): String = DAILY_KEY_PREFIX + date.format(DATE_FORMATTER)

    fun dedup(eventId: UUID, productId: Long): String = "$DEDUP_KEY_PREFIX$eventId:$productId"

    fun carryOverMarker(date: LocalDate): String = CARRY_OVER_MARKER_KEY_PREFIX + date.format(DATE_FORMATTER)
}
