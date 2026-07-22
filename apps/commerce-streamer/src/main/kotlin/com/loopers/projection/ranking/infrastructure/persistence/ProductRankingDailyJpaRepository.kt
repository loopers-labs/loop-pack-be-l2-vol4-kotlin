package com.loopers.projection.ranking.infrastructure.persistence

import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankingDailyJpaRepository : JpaRepository<ProductRankingDailyJpaEntity, ProductRankingDailyJpaId> {
    fun deleteByIdRankingDate(rankingDate: LocalDate)

    fun findByIdRankingDateOrderByRankNoAsc(rankingDate: LocalDate): List<ProductRankingDailyJpaEntity>
}
