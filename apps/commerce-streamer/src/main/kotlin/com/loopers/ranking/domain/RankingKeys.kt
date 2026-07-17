package com.loopers.ranking.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object RankingKeys {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")
    const val CARRY_WEIGHT: Double = 0.1
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun today(date: LocalDate): String = "ranking:all:${date.format(YMD)}"

    fun tail(date: LocalDate): String = "tail:${date.format(YMD)}"

    fun carryMerged(date: LocalDate): String = "carry:merged:${date.format(YMD)}"

    fun expireAtEpochSecond(keyDate: LocalDate): Long =
        keyDate.plusDays(2).atStartOfDay(KST).toEpochSecond()

    fun isTailWindow(now: ZonedDateTime): Boolean {
        val kst = now.withZoneSameInstant(KST)
        return kst.hour == 23 && kst.minute >= 50
    }
}
