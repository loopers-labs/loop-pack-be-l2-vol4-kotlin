package com.loopers.domain.ranking

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

interface RankingCarryOverRepository {
    fun tryAcquireLock(
        date: LocalDate,
        ownerId: String,
        ttl: Duration,
    ): Boolean

    fun carryOver(
        sourceDate: LocalDate,
        targetDate: LocalDate,
        topN: Long,
        factor: Double,
        defaultWeights: RankingWeights,
        expiresAt: Instant,
    ): Long

    fun releaseLock(date: LocalDate, ownerId: String)
}

data class RankingWeights(
    val view: Double,
    val like: Double,
    val sales: Double,
) {
    init {
        require(view.isFinite() && view >= 0.0)
        require(like.isFinite() && like >= 0.0)
        require(sales.isFinite() && sales >= 0.0)
    }
}

class RankingCarryOverUnavailableException(
    cause: Throwable,
) : RuntimeException("Ranking carry-over store is unavailable.", cause)
