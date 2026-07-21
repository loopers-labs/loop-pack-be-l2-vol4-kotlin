package com.loopers.config.redis

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object RankingRedisKeys {
    const val ACTIVE_WEIGHTS = "ranking:weights:active"

    fun view(date: LocalDate): String = "ranking:metric:view:${tag(date)}"

    fun like(date: LocalDate): String = "ranking:metric:like:${tag(date)}"

    fun rawSalesAmount(date: LocalDate): String = "ranking:raw:sales-amount:${tag(date)}"

    fun sales(date: LocalDate): String = "ranking:metric:sales:${tag(date)}"

    fun carry(date: LocalDate): String = "ranking:metric:carry:${tag(date)}"

    fun all(date: LocalDate): String = "ranking:all:${tag(date)}"

    fun weekly(date: LocalDate): String = "ranking:weekly:${tag(date)}"

    fun processed(date: LocalDate): String = "ranking:processed:${tag(date)}"

    fun carryOverLock(date: LocalDate): String = "ranking:carry-over:${tag(date)}:lock"

    private fun tag(date: LocalDate): String {
        return "{${date.format(DateTimeFormatter.BASIC_ISO_DATE)}}"
    }
}
