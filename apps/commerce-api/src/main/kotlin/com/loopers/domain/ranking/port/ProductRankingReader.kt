package com.loopers.domain.ranking.port

import java.time.LocalDate

interface ProductRankingReader {
    fun findProductIds(
        date: LocalDate,
        page: Int,
        size: Int,
    ): List<Long>

    fun count(date: LocalDate): Long

    fun findRank(
        date: LocalDate,
        productId: Long,
    ): Long?
}
