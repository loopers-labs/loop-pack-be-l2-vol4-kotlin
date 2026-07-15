package com.loopers.ranking.infrastructure

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate

interface ProductRankingDailyJpaRepository : JpaRepository<ProductRankingDaily, Long> {
    fun findByRankingDateOrderByScoreDescProductIdAsc(rankingDate: LocalDate, pageable: Pageable): List<ProductRankingDaily>

    fun findByRankingDateAndProductId(rankingDate: LocalDate, productId: Long): ProductRankingDaily?

    fun countByRankingDateAndScoreGreaterThan(rankingDate: LocalDate, score: BigDecimal): Long
}
