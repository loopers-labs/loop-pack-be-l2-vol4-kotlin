package com.loopers.infrastructure.productmetric.repository

import com.loopers.domain.productmetric.ProductMetricMonthly
import com.loopers.domain.productmetric.ProductMetricMonthlyRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
class ProductMetricMonthlyRepositoryImpl(
    private val productMetricMonthlyJpaRepository: ProductMetricMonthlyJpaRepository,
) : ProductMetricMonthlyRepository {
    @Transactional
    override fun upsert(
        baseDate: LocalDate,
        productId: Long,
        viewCount: Long,
        likeCount: Long,
        salesAmount: Long,
    ) {
        productMetricMonthlyJpaRepository.upsert(baseDate, productId, viewCount, likeCount, salesAmount)
    }

    @Transactional
    override fun deleteByBaseDate(baseDate: LocalDate) {
        productMetricMonthlyJpaRepository.deleteByBaseDate(baseDate)
    }

    @Transactional(readOnly = true)
    override fun find(
        baseDate: LocalDate,
        productId: Long,
    ): ProductMetricMonthly? {
        return productMetricMonthlyJpaRepository.findByBaseDateAndProductId(baseDate, productId)?.toDomain()
    }
}
