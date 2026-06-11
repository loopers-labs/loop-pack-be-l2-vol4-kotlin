package com.loopers.domain.productstat

interface ProductStatRepository {
    fun findByProductId(productId: Long): ProductStat?

    fun findByProductIdForUpdate(productId: Long): ProductStat?

    fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat>

    fun save(productStat: ProductStat): ProductStat
}
