package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.PeriodRankingRepositoryPort
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPeriod
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDate

/** 주간/월간 랭킹 MV 조회 어댑터. rank는 배치가 확정해둔 rank_no를 그대로 노출한다. */
@Component
class PeriodRankingRepositoryAdapter(
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
) : PeriodRankingRepositoryPort {

    override fun getPage(period: RankingPeriod, aggregatedDate: LocalDate, page: Int, size: Int): List<RankingEntry> {
        val pageable = PageRequest.of(page - 1, size)
        return when (period) {
            RankingPeriod.WEEKLY ->
                weeklyJpaRepository.findAllByAggregatedDateOrderByRankNoAsc(aggregatedDate, pageable)
                    .map { RankingEntry(productId = it.productId, score = it.score.toDouble(), rank = it.rankNo.toLong()) }

            RankingPeriod.MONTHLY ->
                monthlyJpaRepository.findAllByAggregatedDateOrderByRankNoAsc(aggregatedDate, pageable)
                    .map { RankingEntry(productId = it.productId, score = it.score.toDouble(), rank = it.rankNo.toLong()) }
        }
    }

    override fun getTotalCount(period: RankingPeriod, aggregatedDate: LocalDate): Long = when (period) {
        RankingPeriod.WEEKLY -> weeklyJpaRepository.countByAggregatedDate(aggregatedDate)
        RankingPeriod.MONTHLY -> monthlyJpaRepository.countByAggregatedDate(aggregatedDate)
    }
}
