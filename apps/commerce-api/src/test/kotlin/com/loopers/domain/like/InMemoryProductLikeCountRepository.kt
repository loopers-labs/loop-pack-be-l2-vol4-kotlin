package com.loopers.domain.like

import java.time.ZonedDateTime

class InMemoryProductLikeCountRepository : ProductLikeCountRepository {
    private val store = mutableMapOf<Long, Int>()
    override fun findByProductIdIn(productIds: List<Long>) =
        store.filterKeys { it in productIds }.map { ProductLikeCountModel(it.key, it.value, ZonedDateTime.now()) }
    override fun upsertLikeCount(productId: Long, delta: Int, now: ZonedDateTime): Int {
        store[productId] = maxOf(0, (store[productId] ?: 0) + delta)   // GREATEST(0,..) 흉내
        return 1
    }
}
