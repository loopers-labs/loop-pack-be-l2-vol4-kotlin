package com.loopers.domain.ranking

/**
 * 랭킹 ZSET 저장소 인터페이스.
 * Redis Sorted Set 기반으로 일별 키에 점수를 누적한다.
 */
interface RankingRepository {
    /**
     * 해당 상품의 오늘자 랭킹 점수를 증가시킨다 (ZINCRBY).
     *
     * @param productId 상품 ID (ZSET member)
     * @param score 증가할 점수 (음수면 차감)
     */
    fun incrementScore(productId: Long, score: Double)
}
