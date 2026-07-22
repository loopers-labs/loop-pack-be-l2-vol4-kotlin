package com.loopers.projection.ranking.port

import com.loopers.projection.ranking.application.RankingEntry
import java.time.LocalDate
import java.util.UUID

interface ProductRankingStore {
    fun incrementScore(
        date: LocalDate,
        eventId: UUID,
        productId: Long,
        score: Double,
    ): Boolean

    fun carryOver(
        from: LocalDate,
        to: LocalDate,
        decay: Double,
        minScore: Double,
    ): Boolean

    fun topEntries(
        date: LocalDate,
        limit: Int,
    ): List<RankingEntry>
}
