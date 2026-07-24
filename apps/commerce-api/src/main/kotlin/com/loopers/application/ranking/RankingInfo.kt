package com.loopers.application.ranking

data class RankingInfo(
    val rank: Long,
    val productId: Long,
    val name: String,
    val brandName: String,
    val price: Long,
    val likeCount: Int,
    val soldOut: Boolean,
)
