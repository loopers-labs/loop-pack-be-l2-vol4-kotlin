package com.loopers.domain.ranking

import java.time.LocalDate

interface RankingRepository {
    fun findPage(
        date: LocalDate,
        page: Int,
        size: Int,
    ): RankingPage

    fun findRank(date: LocalDate, productId: Long): Long?
}

data class RankingPage(
    val entries: List<RankingEntry>,
    val totalElements: Long,
)

data class RankingEntry(
    val productId: Long,
    val rank: Long,
    val score: Double,
)

class RankingUnavailableException(
    cause: Throwable,
) : RuntimeException("Ranking store is unavailable.", cause)
