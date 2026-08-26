package com.loopers.infrastructure.ranking

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface MvProductRankWeeklyJpaRepository :
    JpaRepository<MvProductRankWeeklyJpaEntity, MvProductRankId> {

    fun findByPeriodStartOrderByRankAsc(periodStart: LocalDate, pageable: Pageable): List<MvProductRankWeeklyJpaEntity>

    fun countByPeriodStart(periodStart: LocalDate): Long
}
