package com.loopers.config.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class RankingDatePolicyTest {
    private val policy = RankingDatePolicy(RankingRedisProperties())

    @DisplayName("이벤트 시각을 KST 날짜와 고정 만료 시각으로 변환한다")
    @Test
    fun convertsEventTimeToRankingDateAndExpiry() {
        val occurredAt = ZonedDateTime.parse("2026-07-13T16:30:00Z")
        val date = policy.dateOf(occurredAt)

        assertAll(
            { assertThat(date).isEqualTo(LocalDate.of(2026, 7, 14)) },
            { assertThat(policy.expiresAt(date)).isEqualTo(Instant.parse("2026-07-15T15:00:00Z")) },
            { assertThat(policy.isExpired(date, Instant.parse("2026-07-15T14:59:59Z"))).isFalse() },
            { assertThat(policy.isExpired(date, Instant.parse("2026-07-15T15:00:00Z"))).isTrue() },
            { assertThat(RankingRedisKeys.all(date)).isEqualTo("ranking:all:{20260714}") },
            { assertThat(RankingRedisKeys.processed(date)).isEqualTo("ranking:processed:{20260714}") },
        )
    }
}
