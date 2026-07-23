package com.loopers.infrastructure.ranking

import com.loopers.infrastructure.ranking.entity.ProductRankMonthlyEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ProductRankMonthlyReadJpaRepository : JpaRepository<ProductRankMonthlyEntity, Long> {
    fun findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate: LocalDate): List<ProductRankMonthlyEntity>
}
