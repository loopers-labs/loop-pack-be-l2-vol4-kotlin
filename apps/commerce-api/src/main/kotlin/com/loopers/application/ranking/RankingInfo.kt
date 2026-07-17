package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingPeriod
import java.math.BigDecimal

data class RankingItemInfo(
    val rank: Long,
    val productId: Long,
    val name: String,
    val price: BigDecimal,
    val brandName: String,
    val likeCount: Int,
    val score: Double,
)

data class RankingPageInfo(
    val items: List<RankingItemInfo>,
    val period: RankingPeriod,
    val date: String,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)
