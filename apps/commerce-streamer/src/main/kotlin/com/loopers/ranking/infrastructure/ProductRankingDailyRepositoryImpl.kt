package com.loopers.ranking.infrastructure

import com.loopers.ranking.domain.ProductRankingDailyRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class ProductRankingDailyRepositoryImpl(
    private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
) : ProductRankingDailyRepository {
    override fun accumulate(rankingDate: LocalDate, productId: Long, change: BigDecimal) {
        productRankingDailyJpaRepository.upsertChange(rankingDate, productId, change)
    }
}
