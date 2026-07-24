package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class RankingPeriod(val pattern: String, private val keyPrefix: String) {
    // 크로스 앱 키 계약 — commerce-streamer의 RankingKeyResolver(prefix·날짜패턴)와 반드시 동기화할 것
    DAILY("yyyyMMdd", "ranking:all:v1:"),
    HOURLY("yyyyMMddHH", "ranking:hourly:v1:"),
    ;

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern.replace("yyyy", "uuuu"))
            .withResolverStyle(ResolverStyle.STRICT)

    fun resolveDate(date: String?, now: ZonedDateTime): String {
        // 서울 기준으로 정규화 — JVM 기본 시간대(컨테이너 TZ)에 좌우되면 streamer가 적재하는 키와 어긋난다
        if (date.isNullOrBlank()) return now.withZoneSameInstant(RANKING_ZONE).format(formatter)
        if (date.length != pattern.length) {
            throw CoreException(ErrorType.BAD_REQUEST, "date는 $pattern 형식이어야 합니다.")
        }
        runCatching { formatter.parse(date) }
            .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "date는 $pattern 형식이어야 합니다.") }
        return date
    }

    fun key(resolvedDate: String): String = keyPrefix + resolvedDate

    companion object {
        // 크로스 앱 키 계약 — commerce-streamer의 RankingKeyResolver(prefix·날짜패턴·시간대)와 반드시 동기화할 것
        val RANKING_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        fun from(value: String?): RankingPeriod {
            if (value.isNullOrBlank()) return DAILY
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 랭킹 기간입니다. (DAILY, HOURLY)")
        }
    }
}
