package com.loopers.domain.ranking.presentation.response

import com.loopers.domain.ranking.application.info.RankingItemInfo

data class RankingResponse(
    val rank: Long,
    val productId: Long,
    val name: String,
    val price: Long,
    val saleType: String,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
) {
    companion object {
        fun from(info: RankingItemInfo): RankingResponse = RankingResponse(
            rank = info.rank,
            productId = info.productId,
            name = info.name,
            price = info.price,
            saleType = info.saleType,
            brandId = info.brandId,
            brandName = info.brandName,
            likeCount = info.likeCount,
        )
    }
}
