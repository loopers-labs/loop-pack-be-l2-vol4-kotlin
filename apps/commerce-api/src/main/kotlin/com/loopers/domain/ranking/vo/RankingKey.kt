package com.loopers.domain.ranking.vo

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RankingKey {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    private const val DAILY_KEY_PREFIX = "ranking:all:"

    fun daily(date: LocalDate): String = DAILY_KEY_PREFIX + date.format(DATE_FORMATTER)
}
