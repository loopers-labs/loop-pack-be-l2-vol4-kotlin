package com.loopers.ranking.infrastructure

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate

interface RankingFallbackDailyJpaRepository : JpaRepository<RankingFallbackDaily, Long> {
    fun findByRankingDateOrderByScoreDescProductIdAsc(rankingDate: LocalDate, pageable: Pageable): List<RankingFallbackDaily>

    fun findByRankingDateAndProductId(rankingDate: LocalDate, productId: Long): RankingFallbackDaily?

    fun countByRankingDateAndScoreGreaterThan(rankingDate: LocalDate, score: BigDecimal): Long
}
