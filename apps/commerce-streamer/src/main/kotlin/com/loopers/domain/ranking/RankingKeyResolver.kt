package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class RankingWindow(
    val dailyKey: String,
    val hourlyKey: String,
    val dailyExpireAt: Instant,
    val hourlyExpireAt: Instant,
)

@Component
class RankingKeyResolver {
    fun windowFor(now: ZonedDateTime): RankingWindow {
        // 서울 기준으로 정규화 — JVM 기본 시간대(컨테이너 TZ)에 좌우되면 api가 조회하는 키와 어긋난다
        val seoulNow = now.withZoneSameInstant(RANKING_ZONE)
        val dayStart = seoulNow.truncatedTo(ChronoUnit.DAYS)
        val hourStart = seoulNow.truncatedTo(ChronoUnit.HOURS)
        return RankingWindow(
            dailyKey = DAILY_KEY_PREFIX + seoulNow.format(DAILY_FORMAT),
            hourlyKey = HOURLY_KEY_PREFIX + seoulNow.format(HOURLY_FORMAT),
            dailyExpireAt = dayStart.plusDays(2).toInstant(),
            hourlyExpireAt = hourStart.plusHours(2).toInstant(),
        )
    }

    companion object {
        // 크로스 앱 키 계약 — commerce-api의 RankingPeriod(prefix·날짜패턴·시간대)와 반드시 동기화할 것
        val RANKING_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        const val DAILY_KEY_PREFIX = "ranking:all:v1:"
        const val HOURLY_KEY_PREFIX = "ranking:hourly:v1:"
        private val DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")
    }
}
