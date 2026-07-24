package com.loopers.domain.ranking

import java.time.LocalDate

interface RankingRepository {
    fun findTopN(date: LocalDate, offset: Long, count: Long): List<RankedProductId>

    fun countByDate(date: LocalDate): Long

    fun findRank(date: LocalDate, productId: Long): Long?
}
