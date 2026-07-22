package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class RankingPeriodTest {

    @DisplayName("WEEKLY는 기준일이 속한 주의 지난주 월요일을 집계 시작일로 해석한다.")
    @ParameterizedTest(name = "기준일={0} → 시작일={1}")
    @CsvSource(
        "2026-07-20, 2026-07-13", // 월요일
        "2026-07-22, 2026-07-13", // 수요일
        "2026-07-26, 2026-07-13", // 일요일
        "2026-01-01, 2025-12-22", // 연도 경계
    )
    fun weeklyAggregatedDate(date: LocalDate, expected: LocalDate) {
        assertThat(RankingPeriod.WEEKLY.aggregatedDateFor(date)).isEqualTo(expected)
    }

    @DisplayName("MONTHLY는 기준일이 속한 달의 지난달 1일을 집계 시작일로 해석한다.")
    @ParameterizedTest(name = "기준일={0} → 시작일={1}")
    @CsvSource(
        "2026-07-20, 2026-06-01",
        "2026-01-15, 2025-12-01", // 연도 경계
    )
    fun monthlyAggregatedDate(date: LocalDate, expected: LocalDate) {
        assertThat(RankingPeriod.MONTHLY.aggregatedDateFor(date)).isEqualTo(expected)
    }

    @DisplayName("집계 구간의 끝은 주간=시작+6일, 월간=그 달의 말일이다.")
    @Test
    fun endDateOfWindow() {
        assertThat(RankingPeriod.WEEKLY.endDateOf(LocalDate.of(2026, 7, 13))).isEqualTo(LocalDate.of(2026, 7, 19))
        assertThat(RankingPeriod.MONTHLY.endDateOf(LocalDate.of(2026, 6, 1))).isEqualTo(LocalDate.of(2026, 6, 30))
        assertThat(RankingPeriod.MONTHLY.endDateOf(LocalDate.of(2026, 2, 1))).isEqualTo(LocalDate.of(2026, 2, 28))
    }
}
