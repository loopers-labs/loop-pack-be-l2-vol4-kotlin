package com.loopers.domain.product.repository

import com.loopers.domain.product.model.ProductStat

interface ProductStatRepository {
    fun findByProductId(productId: Long): ProductStat?

    fun findByProductIdForUpdate(productId: Long): ProductStat?

    fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat>

    fun save(productStat: ProductStat): ProductStat
}
