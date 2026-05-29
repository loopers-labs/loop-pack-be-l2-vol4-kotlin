package com.loopers.domain.catalog

interface ProductStatsRepository {
    fun save(stats: ProductStats): ProductStats

    fun findByProductId(productId: Long): ProductStats?
}
