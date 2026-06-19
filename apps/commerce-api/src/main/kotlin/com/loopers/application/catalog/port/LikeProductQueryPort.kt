package com.loopers.application.catalog.port

interface LikeProductQueryPort {
    fun getLikedProductIds(userId: Long, productIds: Collection<Long>): Set<Long>

    fun isLiked(userId: Long, productId: Long): Boolean
}
