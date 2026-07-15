package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 일간 랭킹판 키 계산 — `rank:all:{yyyyMMdd}`.
 * 쓰기(streamer)와 읽기(commerce-api)가 같은 규약으로 이 키를 만들어 하나의 랭킹판을 공유한다.
 */
object RankingKey {
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun of(date: LocalDate): String = "rank:all:${date.format(DATE_FORMAT)}"

    // 행동 발생 시각은 Asia/Seoul 벽시계 기준으로 그날 랭킹판에 귀속된다. 두 앱 모두 Asia/Seoul 로 고정돼 날짜부만 취한다.
    fun of(occurredAt: LocalDateTime): String = of(occurredAt.toLocalDate())
}
