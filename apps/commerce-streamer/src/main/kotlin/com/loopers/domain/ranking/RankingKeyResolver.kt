package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import java.time.Instant
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
        val dayStart = now.truncatedTo(ChronoUnit.DAYS)
        val hourStart = now.truncatedTo(ChronoUnit.HOURS)
        return RankingWindow(
            dailyKey = DAILY_KEY_PREFIX + now.format(DAILY_FORMAT),
            hourlyKey = HOURLY_KEY_PREFIX + now.format(HOURLY_FORMAT),
            dailyExpireAt = dayStart.plusDays(2).toInstant(),
            hourlyExpireAt = hourStart.plusHours(2).toInstant(),
        )
    }

    companion object {
        const val DAILY_KEY_PREFIX = "ranking:all:v1:"
        const val HOURLY_KEY_PREFIX = "ranking:hourly:v1:"
        private val DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")
    }
}
