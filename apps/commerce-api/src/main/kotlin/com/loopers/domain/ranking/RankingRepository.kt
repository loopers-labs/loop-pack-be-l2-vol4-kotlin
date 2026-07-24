package com.loopers.domain.ranking

import java.time.LocalDate

interface RankingRepository {
    fun findTopN(date: LocalDate, offset: Long, count: Long): List<RankedProductId>

    fun countByDate(date: LocalDate): Long

    fun findRank(date: LocalDate, productId: Long): Long?

    fun findWeeklyTopN(periodStart: LocalDate, offset: Long, count: Long): List<RankedProductId>

    fun countWeekly(periodStart: LocalDate): Long

    fun findMonthlyTopN(periodStart: LocalDate, offset: Long, count: Long): List<RankedProductId>

    fun countMonthly(periodStart: LocalDate): Long
}
