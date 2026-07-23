package com.loopers.batch.job.productrank

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 랭킹 집계 기간 (달력 기준). targetDate가 속한 주/달의 "직전 완결 기간"을 집계 대상으로 삼는다 —
 * 주간은 지난주 월~일, 월간은 지난달 1일~말일. MV 키(aggregated_date)는 집계 기간의 시작일이다.
 */
enum class RankPeriod(val mvTable: String) {
    WEEKLY("mv_product_rank_weekly") {
        override fun windowFor(targetDate: LocalDate): AggregationWindow {
            val start = targetDate.with(DayOfWeek.MONDAY).minusWeeks(1)
            return AggregationWindow(start = start, endInclusive = start.plusDays(6))
        }
    },
    MONTHLY("mv_product_rank_monthly") {
        override fun windowFor(targetDate: LocalDate): AggregationWindow {
            val start = targetDate.withDayOfMonth(1).minusMonths(1)
            return AggregationWindow(start = start, endInclusive = start.plusMonths(1).minusDays(1))
        }
    },
    ;

    abstract fun windowFor(targetDate: LocalDate): AggregationWindow
}

/** 집계 구간 [start, endInclusive]. aggregated_date(MV 파티션 키)는 start와 같다. */
data class AggregationWindow(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    val aggregatedDate: LocalDate get() = start
}
