package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetricsModel
import com.loopers.domain.metrics.ProductMetricsRepository
import org.springframework.stereotype.Repository

@Repository
class ProductMetricsRepositoryImpl(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {
    override fun upsert(productId: Long, likeDelta: Long, viewDelta: Long, salesDelta: Long) {
        productMetricsJpaRepository.upsert(productId, likeDelta, viewDelta, salesDelta)
    }

    override fun findByProductId(productId: Long): ProductMetricsModel? =
        productMetricsJpaRepository.findById(productId).orElse(null)
}
