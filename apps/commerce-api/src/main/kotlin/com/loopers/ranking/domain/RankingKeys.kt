package com.loopers.ranking.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RankingKeys {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun today(date: LocalDate): String = "ranking:all:${date.format(YMD)}"
}
