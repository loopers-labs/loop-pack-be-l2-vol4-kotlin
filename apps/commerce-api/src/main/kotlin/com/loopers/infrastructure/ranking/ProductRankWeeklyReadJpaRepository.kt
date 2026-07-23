package com.loopers.infrastructure.ranking

import com.loopers.infrastructure.ranking.entity.ProductRankWeeklyEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ProductRankWeeklyReadJpaRepository : JpaRepository<ProductRankWeeklyEntity, Long> {
    fun findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate: LocalDate): List<ProductRankWeeklyEntity>
}
