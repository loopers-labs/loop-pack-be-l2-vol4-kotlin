package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.PeriodType
import com.loopers.domain.ranking.ProductRankModel

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/**
 * 상품 랭킹 MV 저장소.
 */
interface ProductRankRepository : JpaRepository<ProductRankModel, Long> {

    fun findByPeriodTypeAndPeriodKeyOrderByRankingAsc(
        periodType: PeriodType,
        periodKey: String,
    ): List<ProductRankModel>

    @Modifying
    @Query("DELETE FROM ProductRankModel p WHERE p.periodType = :periodType AND p.periodKey = :periodKey")
    fun deleteByPeriodTypeAndPeriodKey(periodType: PeriodType, periodKey: String)
}
