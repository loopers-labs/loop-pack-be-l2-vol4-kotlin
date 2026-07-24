package com.loopers.application.ranking

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object RankingKeyGenerator {
    private const val DAILY_PREFIX = "ranking:all:"
    private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun daily(date: LocalDate): String =
        "$DAILY_PREFIX${date.format(dateFormatter)}"
}
