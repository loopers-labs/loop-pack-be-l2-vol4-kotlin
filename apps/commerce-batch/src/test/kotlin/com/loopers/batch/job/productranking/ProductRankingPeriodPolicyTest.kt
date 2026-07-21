package com.loopers.batch.job.productranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate

class ProductRankingPeriodPolicyTest {
    private val policy = ProductRankingPeriodPolicy()

    @DisplayName("Weekly baseDate에서 직전 월요일부터 baseDate 직전까지의 반개구간을 계산한다")
    @Test
    fun calculatesWeeklySourceRange() {
        val range = policy.weekly(LocalDate.of(2026, 8, 3))

        assertAll(
            { assertThat(range.baseDate).isEqualTo(LocalDate.of(2026, 8, 3)) },
            { assertThat(range.sourceStart).isEqualTo(LocalDate.of(2026, 7, 27)) },
            { assertThat(range.sourceEndExclusive).isEqualTo(LocalDate.of(2026, 8, 3)) },
        )
    }

    @DisplayName("Monthly baseDate에서 직전 달 전체의 반개구간을 계산한다")
    @Test
    fun calculatesMonthlySourceRange() {
        val range = policy.monthly(LocalDate.of(2026, 8, 1))

        assertAll(
            { assertThat(range.baseDate).isEqualTo(LocalDate.of(2026, 8, 1)) },
            { assertThat(range.sourceStart).isEqualTo(LocalDate.of(2026, 7, 1)) },
            { assertThat(range.sourceEndExclusive).isEqualTo(LocalDate.of(2026, 8, 1)) },
        )
    }

    @DisplayName("Weekly baseDate가 월요일이 아니면 실패한다")
    @Test
    fun failsWhenWeeklyBaseDateIsNotMonday() {
        assertThatThrownBy { policy.weekly(LocalDate.of(2026, 8, 4)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Monday")
    }

    @DisplayName("Monthly baseDate가 1일이 아니면 실패한다")
    @Test
    fun failsWhenMonthlyBaseDateIsNotFirstDay() {
        assertThatThrownBy { policy.monthly(LocalDate.of(2026, 8, 2)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("first day")
    }
}
