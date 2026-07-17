package com.loopers.domain.ranking

data class RankedProduct(
    val productId: Long,
    val score: Double,
)

interface RankingQueryRepository {
    /** score 내림차순으로 [offset, offset+size) 구간을 반환한다. */
    fun page(key: String, offset: Long, size: Long): List<RankedProduct>

    fun total(key: String): Long

    /** score 내림차순 0-based 순위. 미진입 시 null. */
    fun rank(key: String, productId: Long): Long?
}
