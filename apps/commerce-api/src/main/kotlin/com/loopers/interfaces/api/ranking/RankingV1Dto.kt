package com.loopers.interfaces.api.ranking

import com.loopers.domain.ranking.RankingProductInfo

/**
 * 랭킹 API DTO.
 */
class RankingV1Dto {

    /**
     * 랭킹 페이지 응답 항목.
     *
     * @property rank 순위 (1-based)
     * @property productId 상품 ID
     * @property productName 상품명
     * @property price 상품 가격
     * @property score 랭킹 점수
     */
    data class RankingResponse(
        val rank: Long,
        val productId: Long,
        val productName: String?,
        val price: Long?,
        val score: Double,
    ) {
        companion object {
            /** RankingProductInfo에서 변환 */
            fun from(info: RankingProductInfo) = RankingResponse(
                rank = info.rank,
                productId = info.productId,
                productName = info.productName,
                price = info.price,
                score = info.score,
            )
        }
    }
}
