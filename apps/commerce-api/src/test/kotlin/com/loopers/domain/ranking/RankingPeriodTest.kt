package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RankingPeriodTest {
    private val now = ZonedDateTime.of(2026, 7, 17, 14, 30, 0, 0, ZoneId.of("Asia/Seoul"))

    @DisplayName("period 미지정은 DAILY, hourly(대소문자 무관)는 HOURLY, 미지원 값은 BAD_REQUEST.")
    @Test
    fun fromParsesPeriod() {
        assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY)
        assertThat(RankingPeriod.from("HOURLY")).isEqualTo(RankingPeriod.HOURLY)
        assertThat(RankingPeriod.from("hourly")).isEqualTo(RankingPeriod.HOURLY)
        assertThatThrownBy { RankingPeriod.from("WEEKLY") }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("date 미지정은 현재 시각 기준으로 해석되고 키가 만들어진다.")
    @Test
    fun resolvesDefaultDate() {
        val daily = RankingPeriod.DAILY.resolveDate(null, now)
        val hourly = RankingPeriod.HOURLY.resolveDate(null, now)
        assertThat(daily).isEqualTo("20260717")
        assertThat(hourly).isEqualTo("2026071714")
        assertThat(RankingPeriod.DAILY.key(daily)).isEqualTo("ranking:all:v1:20260717")
        assertThat(RankingPeriod.HOURLY.key(hourly)).isEqualTo("ranking:hourly:v1:2026071714")
    }

    @DisplayName("형식이 잘못된 date는 BAD_REQUEST를 던진다.")
    @Test
    fun rejectsMalformedDate() {
        assertThatThrownBy { RankingPeriod.DAILY.resolveDate("2026-07-17", now) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { RankingPeriod.DAILY.resolveDate("2026071", now) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { RankingPeriod.HOURLY.resolveDate("20260717", now) }
            .isInstanceOf(CoreException::class.java)
        assertThat(RankingPeriod.DAILY.resolveDate("20260716", now)).isEqualTo("20260716")
    }
}
