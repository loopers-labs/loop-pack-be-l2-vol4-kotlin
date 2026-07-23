package com.loopers.domain.ranking

import java.time.Instant
import java.time.LocalDate

data class CatalogRankingUpdate(
    val eventId: String,
    val productId: Long,
    val date: LocalDate,
    val metric: CatalogRankingMetric,
    val delta: Long,
    val expiresAt: Instant,
)

enum class CatalogRankingMetric {
    VIEW,
    LIKE,
}

data class OrderRankingUpdate(
    val eventId: String,
    val date: LocalDate,
    val items: List<SalesItem>,
    val expiresAt: Instant,
) {
    data class SalesItem(
        val productId: Long,
        val amount: Long,
    )
}

enum class RankingUpdateResult {
    APPLIED,
    DUPLICATE,
}
