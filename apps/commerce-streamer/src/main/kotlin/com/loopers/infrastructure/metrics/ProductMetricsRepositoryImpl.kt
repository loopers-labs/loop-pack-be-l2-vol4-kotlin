package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetricsModel
import com.loopers.domain.metrics.ProductMetricsRepository
import org.springframework.stereotype.Repository

@Repository
class ProductMetricsRepositoryImpl(
    private val jpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {

    override fun findByProductId(productId: Long): ProductMetricsModel? {
        return jpaRepository.findByProductId(productId)
    }

    override fun save(model: ProductMetricsModel): ProductMetricsModel {
        return jpaRepository.save(model)
    }
}
