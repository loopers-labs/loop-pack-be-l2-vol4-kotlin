package com.loopers.domain.ranking

import java.util.UUID

/**
 * 랭킹판 갱신 outbound port. 정렬·점수 누적은 저장소가 원자적으로 처리한다.
 */
interface RankingRepository {
    /**
     * eventId 당 정확히 한 번만 점수를 누적한다. 이미 반영한 이벤트의 재요청은 무시된다.
     * 멱등 판정과 점수 증분이 원자적으로 함께 일어나, 재전달에도 유실·중복이 없다.
     * 랭킹판과 멱등 표식에 보존 기간(ttlSeconds)을 걸어 메모리를 자동 회수한다.
     */
    fun incrementScoreOnce(eventId: UUID, key: String, productId: Long, delta: Double, ttlSeconds: Long)
}
