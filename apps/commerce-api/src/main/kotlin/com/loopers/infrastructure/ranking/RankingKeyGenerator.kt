package com.loopers.infrastructure.ranking

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object RankingKeyGenerator {
    private const val DAILY_PREFIX = "ranking:all:"
    private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun daily(date: LocalDate): String =
        "$DAILY_PREFIX${date.format(dateFormatter)}"

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun monthStart(date: LocalDate): LocalDate =
        date.withDayOfMonth(1)
}
