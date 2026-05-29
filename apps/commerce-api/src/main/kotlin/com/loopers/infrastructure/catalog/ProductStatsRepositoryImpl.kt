package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStats
import com.loopers.domain.catalog.ProductStatsRepository
import org.springframework.stereotype.Component

@Component
class ProductStatsRepositoryImpl(
    private val productStatsJpaRepository: ProductStatsJpaRepository,
) : ProductStatsRepository {
    override fun save(stats: ProductStats): ProductStats = productStatsJpaRepository.save(stats)

    override fun findByProductId(productId: Long): ProductStats? =
        productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(productId)
}
