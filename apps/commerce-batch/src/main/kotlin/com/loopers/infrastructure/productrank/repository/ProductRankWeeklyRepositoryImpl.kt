package com.loopers.infrastructure.productrank.repository

import com.loopers.domain.productrank.ProductRankWeekly
import com.loopers.domain.productrank.ProductRankWeeklyRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
class ProductRankWeeklyRepositoryImpl(
    private val productRankWeeklyJpaRepository: ProductRankWeeklyJpaRepository,
) : ProductRankWeeklyRepository {
    @Transactional
    override fun upsert(
        baseDate: LocalDate,
        productId: Long,
        rankingScore: Double,
    ) {
        productRankWeeklyJpaRepository.upsert(baseDate, productId, rankingScore)
    }

    @Transactional
    override fun deleteByBaseDate(baseDate: LocalDate) {
        productRankWeeklyJpaRepository.deleteByBaseDate(baseDate)
    }

    @Transactional(readOnly = true)
    override fun findTop100(baseDate: LocalDate): List<ProductRankWeekly> {
        return productRankWeeklyJpaRepository.findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate)
    }
}
