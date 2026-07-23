package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingInfo

class RankingV1Dto {
    data class RankingResponse private constructor(
        val rank: Long,
        val productId: Long,
        val name: String,
        val brandName: String,
        val price: Int,
        val likeCount: Int,
    ) {
        companion object {
            fun from(info: RankingInfo) =
                RankingResponse(
                    rank = info.rank,
                    productId = info.productId,
                    name = info.name,
                    brandName = info.brandName,
                    price = info.price,
                    likeCount = info.likeCount,
                )
        }
    }
}
