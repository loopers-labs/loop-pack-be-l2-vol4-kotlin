package com.loopers.domain.ranking

/**
 * 랭킹 조회 저장소 인터페이스.
 * Redis ZSET에서 Top-N 및 개별 상품 순위를 조회한다.
 */
interface RankingQueryRepository {
    /**
     * 지정 날짜의 Top-N 상품을 조회한다.
     *
     * @param date 조회 대상 날짜 (yyyyMMdd)
     * @param offset 시작 위치 (0-based)
     * @param size 조회 개수
     * @return (productId, score) 리스트 (점수 높은 순)
     */
    fun getTopN(date: String, offset: Long, size: Long): List<RankingEntry>

    /**
     * 지정 날짜에서 특정 상품의 순위를 조회한다.
     *
     * @param date 조회 대상 날짜 (yyyyMMdd)
     * @param productId 상품 ID
     * @return 순위 (1-based). 랭킹에 없으면 null.
     */
    fun getRank(date: String, productId: Long): Long?

    /**
     * 지정 날짜에서 특정 상품의 점수를 조회한다.
     *
     * @param date 조회 대상 날짜 (yyyyMMdd)
     * @param productId 상품 ID
     * @return 점수. 없으면 null.
     */
    fun getScore(date: String, productId: Long): Double?
}

/**
 * 랭킹 엔트리 (productId + score).
 */
data class RankingEntry(
    val productId: Long,
    val score: Double,
)
