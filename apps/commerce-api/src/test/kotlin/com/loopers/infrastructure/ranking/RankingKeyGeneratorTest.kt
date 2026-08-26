package com.loopers.infrastructure.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RankingKeyGeneratorTest {
    @Test
    fun `일간 랭킹 키를 yyyyMMdd 형식으로 생성한다`() {
        // arrange
        val date = LocalDate.of(2026, 7, 13)

        // act
        val key = RankingKeyGenerator.daily(date)

        // assert
        assertThat(key).isEqualTo("ranking:all:20260713")
    }

    @Test
    fun `날짜에서 해당 주의 월요일을 계산한다`() {
        val date = LocalDate.of(2025, 7, 3)
        val result = RankingKeyGenerator.weekStart(date)
        assertThat(result).isEqualTo(LocalDate.of(2025, 6, 30))
    }

    @Test
    fun `월요일 자체를 넣으면 그대로 반환한다`() {
        val monday = LocalDate.of(2025, 6, 30)
        val result = RankingKeyGenerator.weekStart(monday)
        assertThat(result).isEqualTo(monday)
    }

    @Test
    fun `날짜에서 해당 월 1일을 계산한다`() {
        val date = LocalDate.of(2025, 7, 15)
        val result = RankingKeyGenerator.monthStart(date)
        assertThat(result).isEqualTo(LocalDate.of(2025, 7, 1))
    }
}
