package com.loopers.infrastructure.productmetric.repository

import com.loopers.domain.productmetric.ProductMetricDaily
import com.loopers.domain.productmetric.ProductMetricDailyRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class ProductMetricDailyRepositoryImpl(
    private val productMetricDailyJpaRepository: ProductMetricDailyJpaRepository,
) : ProductMetricDailyRepository {
    @Transactional
    override fun increment(
        metricDate: LocalDate,
        productId: Long,
        viewCountDelta: Long,
        likeCountDelta: Long,
        salesAmountDelta: Long,
    ) {
        productMetricDailyJpaRepository.upsertIncrement(
            metricDate = metricDate,
            productId = productId,
            viewCountDelta = viewCountDelta,
            likeCountDelta = likeCountDelta,
            salesAmountDelta = salesAmountDelta,
        )
    }

    override fun find(
        metricDate: LocalDate,
        productId: Long,
    ): ProductMetricDaily? {
        return productMetricDailyJpaRepository.findByMetricDateAndProductId(
            metricDate = metricDate,
            productId = productId,
        )?.toDomain()
    }
}
