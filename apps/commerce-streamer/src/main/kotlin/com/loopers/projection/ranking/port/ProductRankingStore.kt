package com.loopers.projection.ranking.port

import java.time.LocalDate
import java.util.UUID

interface ProductRankingStore {
    fun incrementScore(
        date: LocalDate,
        eventId: UUID,
        productId: Long,
        score: Double,
    ): Boolean
}
