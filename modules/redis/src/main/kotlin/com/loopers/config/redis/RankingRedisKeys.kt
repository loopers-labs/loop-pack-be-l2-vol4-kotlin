package com.loopers.config.redis

import java.time.LocalDate

object RankingRedisKeys {
    const val ACTIVE_WEIGHTS = "ranking:weights:active"

    fun view(date: LocalDate): String = "not-implemented"

    fun like(date: LocalDate): String = "not-implemented"

    fun rawSalesAmount(date: LocalDate): String = "not-implemented"

    fun sales(date: LocalDate): String = "not-implemented"

    fun carry(date: LocalDate): String = "not-implemented"

    fun all(date: LocalDate): String = "not-implemented"

    fun processed(date: LocalDate): String = "not-implemented"

    fun carryOverLock(date: LocalDate): String = "not-implemented"
}
