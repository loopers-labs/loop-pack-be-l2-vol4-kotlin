package com.loopers.domain.ranking

data class RankingScoreDelta(
    val productId: Long,
    val score: Double,
)

data class RankingScoreEntry(
    val eventId: String,
    val deltas: List<RankingScoreDelta>,
)

interface RankingRepository {
    /**
     * eventId dedup(SET NX)을 통과한 엔트리만 daily/hourly ZSET에 가산하고 적용 건수를 반환한다.
     * 윈도우 키에는 절대시각 만료(EXPIREAT)를 설정한다.
     */
    fun applyAll(entries: List<RankingScoreEntry>, window: RankingWindow): Int
}
