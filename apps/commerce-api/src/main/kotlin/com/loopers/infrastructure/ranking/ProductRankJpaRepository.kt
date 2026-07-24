package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.PeriodType
import com.loopers.domain.ranking.ProductRankModel
import org.springframework.data.jpa.repository.JpaRepository

/**
 * MV 랭킹 조회용 JPA Repository.
 */
interface ProductRankJpaRepository : JpaRepository<ProductRankModel, Long> {

    fun findByPeriodTypeAndPeriodKeyOrderByRankingAsc(
        periodType: PeriodType,
        periodKey: String,
    ): List<ProductRankModel>
}
