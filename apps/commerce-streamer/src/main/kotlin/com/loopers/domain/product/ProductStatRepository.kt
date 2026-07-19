package com.loopers.domain.product

interface ProductStatRepository {
    fun findByProductIdForUpdate(productId: Long): ProductStat?

    fun save(productStat: ProductStat): ProductStat
}
