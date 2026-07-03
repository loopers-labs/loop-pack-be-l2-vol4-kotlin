package com.loopers.metrics.infrastructure

import com.loopers.metrics.domain.ProductMetricsRepository
import org.springframework.stereotype.Repository

@Repository
class ProductMetricsRepositoryImpl(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {
    override fun upsertDelta(productId: Long, likeDelta: Long, salesDelta: Long, viewDelta: Long) {
        productMetricsJpaRepository.upsertDelta(productId, likeDelta, salesDelta, viewDelta)
    }
}
