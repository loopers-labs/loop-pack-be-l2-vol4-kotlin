package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductDetailInfo
import com.loopers.domain.ranking.RankingInfo

/**
 * 상품 API DTO.
 */
class ProductV1Dto {

    /**
     * 상품 조회 응답.
     * 랭킹 정보는 오늘 기준 순위가 존재할 때만 포함된다.
     */
    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val stock: Long,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
        val ranking: RankingResponse?,
    ) {
        companion object {
            /** 랭킹 정보 없이 변환 */
            fun from(info: ProductDetailInfo) = ProductResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                stock = info.stock,
                brandId = info.brandId,
                brandName = info.brandName,
                likeCount = info.likeCount,
                ranking = null,
            )

            /** 랭킹 정보 포함 변환 */
            fun from(info: ProductDetailInfo, rankingInfo: RankingInfo?) = ProductResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                stock = info.stock,
                brandId = info.brandId,
                brandName = info.brandName,
                likeCount = info.likeCount,
                ranking = rankingInfo?.let { RankingResponse(rank = it.rank, score = it.score) },
            )
        }
    }

    /**
     * 상품 내 랭킹 정보.
     */
    data class RankingResponse(
        val rank: Long,
        val score: Double,
    )
}
