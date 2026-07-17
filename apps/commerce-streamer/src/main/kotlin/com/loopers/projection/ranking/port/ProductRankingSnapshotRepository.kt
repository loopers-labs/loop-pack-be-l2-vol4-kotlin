package com.loopers.projection.ranking.port

import com.loopers.projection.ranking.application.RankingEntry
import java.time.LocalDate

interface ProductRankingSnapshotRepository {
    fun replaceAll(
        date: LocalDate,
        entries: List<RankingEntry>,
    )
}
