package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class RankingPeriod(val pattern: String, private val keyPrefix: String) {
    DAILY("yyyyMMdd", "ranking:all:v1:"),
    HOURLY("yyyyMMddHH", "ranking:hourly:v1:"),
    ;

    fun resolveDate(date: String?, now: ZonedDateTime): String {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        if (date.isNullOrBlank()) return now.format(formatter)
        if (date.length != pattern.length) {
            throw CoreException(ErrorType.BAD_REQUEST, "date는 $pattern 형식이어야 합니다.")
        }
        runCatching { formatter.parse(date) }
            .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "date는 $pattern 형식이어야 합니다.") }
        return date
    }

    fun key(resolvedDate: String): String = keyPrefix + resolvedDate

    companion object {
        fun from(value: String?): RankingPeriod {
            if (value.isNullOrBlank()) return DAILY
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 랭킹 기간입니다. (DAILY, HOURLY)")
        }
    }
}
