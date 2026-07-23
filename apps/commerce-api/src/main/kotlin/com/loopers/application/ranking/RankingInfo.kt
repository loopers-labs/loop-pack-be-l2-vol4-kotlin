package com.loopers.application.ranking

data class RankingInfo private constructor(
    val rank: Long,
    val productId: Long,
    val name: String,
    val brandName: String,
    val price: Int,
    val likeCount: Int,
) {
    companion object {
        fun of(rank: Long, productId: Long, name: String, brandName: String, price: Int, likeCount: Int) =
            RankingInfo(rank, productId, name, brandName, price, likeCount)
    }
}
