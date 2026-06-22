package com.loopers.domain.like.port

interface LikeBulkRepository {
    fun seedLikesByDistribution(maxLikesPerProduct: Int): Int

    fun deriveLikeCounts(): Int

    fun countLikes(): Long

    fun countLikeCounts(): Long
}
