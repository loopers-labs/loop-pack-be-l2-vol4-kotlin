package com.loopers.projection.ranking.application

import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RankingKeyTest {
    @Test
    fun `날짜로_일간_랭킹_키를_yyyyMMdd_포맷으로_만든다`() {
        val key = RankingKey.daily(LocalDate.of(2026, 7, 17))

        assertThat(key).isEqualTo("ranking:all:20260717")
    }

    @Test
    fun `이벤트와_상품으로_dedup_키를_만든다`() {
        val eventId = UUID.fromString("11111111-2222-3333-4444-555555555555")

        val key = RankingKey.dedup(eventId, 42L)

        assertThat(key).isEqualTo("ranking:dedup:11111111-2222-3333-4444-555555555555:42")
    }

    @Test
    fun `날짜로_carry_over_마커_키를_만든다`() {
        val key = RankingKey.carryOverMarker(LocalDate.of(2026, 7, 18))

        assertThat(key).isEqualTo("ranking:carryover:20260718")
    }
}
