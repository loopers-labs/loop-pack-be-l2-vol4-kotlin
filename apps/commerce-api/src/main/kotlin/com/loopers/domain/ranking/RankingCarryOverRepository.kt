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
