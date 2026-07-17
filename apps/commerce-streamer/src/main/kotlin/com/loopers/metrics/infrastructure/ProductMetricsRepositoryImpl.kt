package com.loopers.metrics.infrastructure

import com.loopers.metrics.domain.ProductMetricsRepository
import org.springframework.stereotype.Repository

@Repository
class ProductMetricsRepositoryImpl(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {
    override fun accumulate(productId: Long, likeChange: Long, salesChange: Long, viewChange: Long) {
        productMetricsJpaRepository.upsertChanges(productId, likeChange, salesChange, viewChange)
    }
}
