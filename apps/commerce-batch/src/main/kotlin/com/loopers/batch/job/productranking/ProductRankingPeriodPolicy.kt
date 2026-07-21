package com.loopers.batch.job.productranking

import java.time.DayOfWeek
import java.time.LocalDate

data class ProductRankingSourceRange(
    val baseDate: LocalDate,
    val sourceStart: LocalDate,
    val sourceEndExclusive: LocalDate,
)

class ProductRankingPeriodPolicy {
    fun calculate(
        period: ProductRankingPeriod,
        baseDate: LocalDate,
    ): ProductRankingSourceRange {
        return when (period) {
            ProductRankingPeriod.WEEKLY -> weekly(baseDate)
            ProductRankingPeriod.MONTHLY -> monthly(baseDate)
        }
    }

    fun weekly(baseDate: LocalDate): ProductRankingSourceRange {
        require(baseDate.dayOfWeek == DayOfWeek.MONDAY) {
            "Weekly product ranking baseDate must be Monday."
        }
        return ProductRankingSourceRange(
            baseDate = baseDate,
            sourceStart = baseDate.minusDays(7),
            sourceEndExclusive = baseDate,
        )
    }

    fun monthly(baseDate: LocalDate): ProductRankingSourceRange {
        require(baseDate.dayOfMonth == 1) {
            "Monthly product ranking baseDate must be the first day of month."
        }
        return ProductRankingSourceRange(
            baseDate = baseDate,
            sourceStart = baseDate.minusMonths(1),
            sourceEndExclusive = baseDate,
        )
    }
}
