package com.loopers.infrastructure.productmetric.repository

import com.loopers.domain.productmetric.ProductMetricWeekly
import com.loopers.domain.productmetric.ProductMetricWeeklyRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
class ProductMetricWeeklyRepositoryImpl(
    private val productMetricWeeklyJpaRepository: ProductMetricWeeklyJpaRepository,
) : ProductMetricWeeklyRepository {
    @Transactional
    override fun upsert(
        baseDate: LocalDate,
        productId: Long,
        viewCount: Long,
        likeCount: Long,
        salesAmount: Long,
    ) {
        productMetricWeeklyJpaRepository.upsert(baseDate, productId, viewCount, likeCount, salesAmount)
    }

    @Transactional
    override fun deleteByBaseDate(baseDate: LocalDate) {
        productMetricWeeklyJpaRepository.deleteByBaseDate(baseDate)
    }

    @Transactional(readOnly = true)
    override fun find(
        baseDate: LocalDate,
        productId: Long,
    ): ProductMetricWeekly? {
        return productMetricWeeklyJpaRepository.findByBaseDateAndProductId(baseDate, productId)
    }
}
