package com.loopers.domain.ranking

/**
 * 랭킹 반영 이벤트 타입. 가중치는 논리값(1/5/50)에 ×10한 저장 스케일 —
 * 이월(carry-over) 계수 0.1을 곱해도 점수가 정수를 유지해야 한다.
 */
enum class RankingEventType(val weight: Long) {
    VIEW(10),
    LIKE(50),
    ORDER(500),
}
