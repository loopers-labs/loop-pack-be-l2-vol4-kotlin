package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 일간 랭킹판 키 계산 — `rank:all:{yyyyMMdd}`.
 * 읽기(commerce-api)와 쓰기(streamer)가 같은 규약으로 이 키를 만들어 하나의 랭킹판을 공유한다.
 * 조회 파라미터로 받은 날짜 문자열을 검증하고, 없으면 오늘로 기본 지정한다.
 */
object RankingKey {
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun of(date: LocalDate): String = "rank:all:${date.format(DATE_FORMAT)}"

    fun of(dateString: String?, today: LocalDate): String = of(resolveDate(dateString, today))

    private fun resolveDate(dateString: String?, today: LocalDate): LocalDate {
        if (dateString.isNullOrBlank()) return today
        return try {
            LocalDate.parse(dateString, DATE_FORMAT)
        } catch (e: DateTimeParseException) {
            throw CoreException(RankingErrorType.RANKING_BAD_REQUEST, "날짜 형식은 yyyyMMdd 여야 합니다: $dateString")
        }
    }
}
