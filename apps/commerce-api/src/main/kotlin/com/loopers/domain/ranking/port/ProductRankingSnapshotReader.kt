package com.loopers.domain.ranking.port

import java.time.LocalDate

interface ProductRankingSnapshotReader {
    fun findProductIds(
        date: LocalDate,
        page: Int,
        size: Int,
    ): List<Long>

    fun count(date: LocalDate): Long
}
