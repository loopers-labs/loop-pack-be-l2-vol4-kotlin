package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RankingKeyTest {
    @Test
    fun `날짜로 랭킹판 키를 만들면 rank_all_yyyyMMdd 형식이 된다`() {
        val key = RankingKey.of(LocalDate.of(2026, 7, 14))

        assertThat(key).isEqualTo("rank:all:20260714")
    }

    @Test
    fun `자정 직후 시각은 그 날짜의 키에 귀속된다`() {
        val key = RankingKey.of(LocalDateTime.of(2026, 7, 14, 0, 0, 1))

        assertThat(key).isEqualTo("rank:all:20260714")
    }

    @Test
    fun `자정 직전 시각은 전날 키에 귀속된다`() {
        val key = RankingKey.of(LocalDateTime.of(2026, 7, 13, 23, 59, 59))

        assertThat(key).isEqualTo("rank:all:20260713")
    }
}
