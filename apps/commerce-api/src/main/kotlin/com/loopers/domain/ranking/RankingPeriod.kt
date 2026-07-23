package com.loopers.domain.ranking

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 기간 랭킹의 집계 기간 (달력 기준). 기준일이 속한 주/달의 "직전 완결 기간"을 가리키며,
 * MV의 파티션 키(aggregated_date)는 그 기간의 시작일이다 — 배치(commerce-batch)의 윈도우 계산과 동일한 규칙.
 */
enum class RankingPeriod {
    WEEKLY {
        override fun aggregatedDateFor(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY).minusWeeks(1)
        override fun endDateOf(aggregatedDate: LocalDate): LocalDate = aggregatedDate.plusDays(6)
    },
    MONTHLY {
        override fun aggregatedDateFor(date: LocalDate): LocalDate = date.withDayOfMonth(1).minusMonths(1)
        override fun endDateOf(aggregatedDate: LocalDate): LocalDate = aggregatedDate.plusMonths(1).minusDays(1)
    },
    ;

    /** 기준일 → 직전 완결 기간의 시작일 (MV 조회 키). */
    abstract fun aggregatedDateFor(date: LocalDate): LocalDate

    /** 집계 기간의 마지막 날 (응답 표시용). */
    abstract fun endDateOf(aggregatedDate: LocalDate): LocalDate
}
