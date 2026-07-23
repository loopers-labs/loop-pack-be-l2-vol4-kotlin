package com.loopers.batch.job.productrank

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class RankPeriodTest {

    @DisplayName("WEEKLY 윈도우는, ")
    @Nested
    inner class Weekly {
        @DisplayName("targetDate가 주중 어느 요일이든 같은 주면, 지난주 월~일이 나온다.")
        @ParameterizedTest(name = "targetDate={0}")
        // 월요일, 수요일, 일요일 — 같은 주의 서로 다른 요일
        @CsvSource(
            "2026-07-20",
            "2026-07-22",
            "2026-07-26",
        )
        fun returnsLastCalendarWeek_whateverDayOfWeek(targetDate: LocalDate) {
            val window = RankPeriod.WEEKLY.windowFor(targetDate)

            assertThat(window.start).isEqualTo(LocalDate.of(2026, 7, 13))
            assertThat(window.endInclusive).isEqualTo(LocalDate.of(2026, 7, 19))
        }

        @DisplayName("연도 경계를 걸치면, 작년 12월의 주가 나온다.")
        @Test
        fun crossesYearBoundary() {
            val window = RankPeriod.WEEKLY.windowFor(LocalDate.of(2026, 1, 1)) // 목요일

            assertThat(window.start).isEqualTo(LocalDate.of(2025, 12, 22))
            assertThat(window.endInclusive).isEqualTo(LocalDate.of(2025, 12, 28))
        }
    }

    @DisplayName("MONTHLY 윈도우는, ")
    @Nested
    inner class Monthly {
        @DisplayName("targetDate가 속한 달의 지난달 1일~말일이 나온다.")
        @Test
        fun returnsLastCalendarMonth() {
            val window = RankPeriod.MONTHLY.windowFor(LocalDate.of(2026, 7, 20))

            assertThat(window.start).isEqualTo(LocalDate.of(2026, 6, 1))
            assertThat(window.endInclusive).isEqualTo(LocalDate.of(2026, 6, 30))
        }

        @DisplayName("1월이면 작년 12월이 나온다 (연도 경계).")
        @Test
        fun crossesYearBoundary() {
            val window = RankPeriod.MONTHLY.windowFor(LocalDate.of(2026, 1, 15))

            assertThat(window.start).isEqualTo(LocalDate.of(2025, 12, 1))
            assertThat(window.endInclusive).isEqualTo(LocalDate.of(2025, 12, 31))
        }

        @DisplayName("3월이면 2월 말일(평년 28일)까지 나온다.")
        @Test
        fun handlesShortMonth() {
            val window = RankPeriod.MONTHLY.windowFor(LocalDate.of(2026, 3, 5))

            assertThat(window.start).isEqualTo(LocalDate.of(2026, 2, 1))
            assertThat(window.endInclusive).isEqualTo(LocalDate.of(2026, 2, 28))
        }
    }

    @DisplayName("aggregatedDate(MV 키)는 항상 윈도우 시작일과 같다.")
    @Test
    fun aggregatedDateEqualsWindowStart() {
        val weekly = RankPeriod.WEEKLY.windowFor(LocalDate.of(2026, 7, 20))
        val monthly = RankPeriod.MONTHLY.windowFor(LocalDate.of(2026, 7, 20))

        assertThat(weekly.aggregatedDate).isEqualTo(weekly.start)
        assertThat(monthly.aggregatedDate).isEqualTo(monthly.start)
    }
}
