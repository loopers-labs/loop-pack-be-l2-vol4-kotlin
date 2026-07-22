package com.loopers.domain.ranking.application.info

data class RankingItemInfo(
    val rank: Long,
    val productId: Long,
    val name: String,
    val price: Long,
    val saleType: String,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
)
