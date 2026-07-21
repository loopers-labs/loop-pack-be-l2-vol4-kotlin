package com.loopers.infrastructure.productrank.repository

import com.loopers.domain.productrank.ProductRankMonthly
import com.loopers.domain.productrank.ProductRankMonthlyRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
class ProductRankMonthlyRepositoryImpl(
    private val productRankMonthlyJpaRepository: ProductRankMonthlyJpaRepository,
) : ProductRankMonthlyRepository {
    @Transactional
    override fun upsert(
        baseDate: LocalDate,
        productId: Long,
        rankingScore: Double,
    ) {
        productRankMonthlyJpaRepository.upsert(baseDate, productId, rankingScore)
    }

    @Transactional
    override fun deleteByBaseDate(baseDate: LocalDate) {
        productRankMonthlyJpaRepository.deleteByBaseDate(baseDate)
    }

    @Transactional(readOnly = true)
    override fun findTop100(baseDate: LocalDate): List<ProductRankMonthly> {
        return productRankMonthlyJpaRepository.findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate)
    }
}
