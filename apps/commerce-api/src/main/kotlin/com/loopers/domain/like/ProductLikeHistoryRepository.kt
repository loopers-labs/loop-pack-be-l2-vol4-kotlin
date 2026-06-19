package com.loopers.domain.like

interface ProductLikeHistoryRepository {
    fun findById(id: Long): ProductLikeHistory?

    fun findLatest(userId: Long, productId: Long): ProductLikeHistory?

    fun save(history: ProductLikeHistory): ProductLikeHistory

    fun findLikedProductIds(userId: Long, productIds: Collection<Long>): Set<Long>
}
