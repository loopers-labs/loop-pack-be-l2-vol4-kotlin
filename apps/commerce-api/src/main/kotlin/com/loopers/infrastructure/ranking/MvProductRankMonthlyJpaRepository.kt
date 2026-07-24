package com.loopers.infrastructure.ranking

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface MvProductRankMonthlyJpaRepository :
    JpaRepository<MvProductRankMonthlyJpaEntity, MvProductRankId> {

    fun findByPeriodStartOrderByRankAsc(periodStart: LocalDate, pageable: Pageable): List<MvProductRankMonthlyJpaEntity>

    fun countByPeriodStart(periodStart: LocalDate): Long
}
