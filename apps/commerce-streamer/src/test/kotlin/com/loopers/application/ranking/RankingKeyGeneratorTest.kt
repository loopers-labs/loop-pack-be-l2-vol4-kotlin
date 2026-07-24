package com.loopers.application.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RankingKeyGeneratorTest {
    @Test
    fun `일간 랭킹 키를 yyyyMMdd 형식으로 생성한다`() {
        val date = LocalDate.of(2026, 7, 13)

        val key = RankingKeyGenerator.daily(date)

        assertThat(key).isEqualTo("ranking:all:20260713")
    }
}
