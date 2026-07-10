package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetric
import com.loopers.domain.metrics.ProductMetricRepository
import org.springframework.stereotype.Component

@Component
class ProductMetricRepositoryImpl(
    private val jpa: ProductMetricJpaRepository,
) : ProductMetricRepository {
    override fun upsertDelta(productId: Long, likeDelta: Long, salesDelta: Long, viewDelta: Long) =
        jpa.upsertDelta(productId, likeDelta, salesDelta, viewDelta)

    override fun findByProductId(productId: Long): ProductMetric? = jpa.findById(productId).orElse(null)
}
