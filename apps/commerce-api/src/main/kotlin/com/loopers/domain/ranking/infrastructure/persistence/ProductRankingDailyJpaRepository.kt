package com.loopers.domain.ranking.infrastructure.persistence

import java.time.LocalDate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankingDailyJpaRepository : JpaRepository<ProductRankingDailyJpaEntity, ProductRankingDailyJpaId> {
    fun findByIdRankingDateOrderByRankNoAsc(
        rankingDate: LocalDate,
        pageable: Pageable,
    ): List<ProductRankingDailyJpaEntity>

    fun countByIdRankingDate(rankingDate: LocalDate): Long
}
