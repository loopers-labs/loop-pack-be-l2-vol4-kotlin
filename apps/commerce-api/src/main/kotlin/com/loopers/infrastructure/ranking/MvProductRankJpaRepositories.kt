package com.loopers.infrastructure.ranking

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface MvProductRankWeeklyJpaRepository : JpaRepository<MvProductRankWeeklyEntity, Long> {
    fun findAllByAggregatedDateOrderByRankNoAsc(aggregatedDate: LocalDate, pageable: Pageable): List<MvProductRankWeeklyEntity>

    fun countByAggregatedDate(aggregatedDate: LocalDate): Long
}

interface MvProductRankMonthlyJpaRepository : JpaRepository<MvProductRankMonthlyEntity, Long> {
    fun findAllByAggregatedDateOrderByRankNoAsc(aggregatedDate: LocalDate, pageable: Pageable): List<MvProductRankMonthlyEntity>

    fun countByAggregatedDate(aggregatedDate: LocalDate): Long
}
