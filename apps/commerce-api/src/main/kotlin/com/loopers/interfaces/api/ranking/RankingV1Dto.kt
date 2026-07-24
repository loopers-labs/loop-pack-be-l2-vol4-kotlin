package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingInfo

class RankingV1Dto {
    data class RankingItemResponse(
        val rank: Long,
        val productId: Long,
        val name: String,
        val brandName: String,
        val price: Long,
        val likeCount: Int,
        val soldOut: Boolean,
    ) {
        companion object {
            fun from(info: RankingInfo): RankingItemResponse =
                RankingItemResponse(
                    rank = info.rank,
                    productId = info.productId,
                    name = info.name,
                    brandName = info.brandName,
                    price = info.price,
                    likeCount = info.likeCount,
                    soldOut = info.soldOut,
                )
        }
    }
}
