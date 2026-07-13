package com.loopers.interfaces.api.ranking.dto

import com.loopers.application.ranking.dto.RankingProductInfo

class RankingV1Dto {
    data class RankingResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val imageUrl: String,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
        val rank: Long,
        val score: Double,
    ) {
        companion object {
            fun from(info: RankingProductInfo): RankingResponse {
                return RankingResponse(
                    productId = info.productId,
                    productName = info.productName,
                    price = info.price,
                    imageUrl = info.imageUrl,
                    brandId = info.brandId,
                    brandName = info.brandName,
                    likeCount = info.likeCount,
                    rank = info.rank,
                    score = info.score,
                )
            }
        }
    }
}
