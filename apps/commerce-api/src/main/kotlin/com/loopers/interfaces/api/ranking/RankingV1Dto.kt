package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingItemInfo
import com.loopers.application.ranking.RankingPageInfo
import java.math.BigDecimal

class RankingV1Dto {
    data class RankingPageResponse(
        val items: List<RankingItemResponse>,
        val period: String,
        val date: String,
        val page: Int,
        val size: Int,
        val totalCount: Long,
    ) {
        companion object {
            fun from(info: RankingPageInfo): RankingPageResponse {
                return RankingPageResponse(
                    items = info.items.map { RankingItemResponse.from(it) },
                    period = info.period.name,
                    date = info.date,
                    page = info.page,
                    size = info.size,
                    totalCount = info.totalCount,
                )
            }
        }
    }

    data class RankingItemResponse(
        val rank: Long,
        val productId: Long,
        val name: String,
        val price: BigDecimal,
        val brandName: String,
        val likeCount: Int,
        val score: Double,
    ) {
        companion object {
            fun from(info: RankingItemInfo): RankingItemResponse {
                return RankingItemResponse(
                    rank = info.rank,
                    productId = info.productId,
                    name = info.name,
                    price = info.price,
                    brandName = info.brandName,
                    likeCount = info.likeCount,
                    score = info.score,
                )
            }
        }
    }
}
