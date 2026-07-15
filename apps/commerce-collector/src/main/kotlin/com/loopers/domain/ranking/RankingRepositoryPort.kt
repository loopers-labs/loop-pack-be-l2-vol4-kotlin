package com.loopers.domain.ranking

interface RankingRepositoryPort {
    /**
     * 한 이벤트의 모든 보드 갱신을 dedup 키 아래 원자적으로 반영한다.
     *
     * @return true = 실제 반영 / false = dedup에 의해 skip(점수가 이미 반영돼 있음).
     * false는 "Redis 반영 후 커밋 실패 → 재전송" 재시도의 정상 경로이므로 예외로 다루면 안 된다.
     */
    fun incrementScore(entries: List<BoardScore>, productId: Long, eventId: String): Boolean
}
