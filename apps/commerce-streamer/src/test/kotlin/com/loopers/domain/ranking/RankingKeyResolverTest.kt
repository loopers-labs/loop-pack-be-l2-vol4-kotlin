package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RankingKeyResolverTest {
    private val resolver = RankingKeyResolver()
    private val zone = ZoneId.of("Asia/Seoul")

    @DisplayName("일간 키는 ranking:all:v1:yyyyMMdd, 시간 키는 ranking:hourly:v1:yyyyMMddHH 형식이다.")
    @Test
    fun keyFormats() {
        val now = ZonedDateTime.of(2026, 7, 17, 14, 30, 0, 0, zone)
        val window = resolver.windowFor(now)
        assertThat(window.dailyKey).isEqualTo("ranking:all:v1:20260717")
        assertThat(window.hourlyKey).isEqualTo("ranking:hourly:v1:2026071714")
    }

    @DisplayName("만료시각은 윈도우 시작 + 2×윈도우 크기 절대시각이다 (daily +2일, hourly +2시간).")
    @Test
    fun expireAtIsAbsolute() {
        val now = ZonedDateTime.of(2026, 7, 17, 14, 30, 45, 0, zone)
        val window = resolver.windowFor(now)
        assertThat(window.dailyExpireAt)
            .isEqualTo(ZonedDateTime.of(2026, 7, 19, 0, 0, 0, 0, zone).toInstant())
        assertThat(window.hourlyExpireAt)
            .isEqualTo(ZonedDateTime.of(2026, 7, 17, 16, 0, 0, 0, zone).toInstant())
    }

    @DisplayName("호출부 시간대와 무관하게 서울 기준 키를 만든다 — UTC 컨테이너에서도 KST 날짜.")
    @Test
    fun normalizesToSeoulZone() {
        // 2026-07-17 16:30 UTC = 2026-07-18 01:30 KST — UTC 날짜와 KST 날짜가 갈리는 시각
        val utcNow = ZonedDateTime.of(2026, 7, 17, 16, 30, 0, 0, ZoneId.of("UTC"))

        val window = resolver.windowFor(utcNow)

        assertThat(window.dailyKey).isEqualTo("ranking:all:v1:20260718")
        assertThat(window.hourlyKey).isEqualTo("ranking:hourly:v1:2026071801")
        assertThat(window.dailyExpireAt)
            .isEqualTo(ZonedDateTime.of(2026, 7, 20, 0, 0, 0, 0, zone).toInstant())
        assertThat(window.hourlyExpireAt)
            .isEqualTo(ZonedDateTime.of(2026, 7, 18, 3, 0, 0, 0, zone).toInstant())
    }

    @DisplayName("자정 직전/직후는 다른 일간 키를 만든다 — 윈도우 경계.")
    @Test
    fun midnightBoundary() {
        val beforeMidnight = ZonedDateTime.of(2026, 7, 17, 23, 59, 59, 0, zone)
        val afterMidnight = ZonedDateTime.of(2026, 7, 18, 0, 0, 0, 0, zone)
        assertThat(resolver.windowFor(beforeMidnight).dailyKey).isEqualTo("ranking:all:v1:20260717")
        assertThat(resolver.windowFor(afterMidnight).dailyKey).isEqualTo("ranking:all:v1:20260718")
    }
}
