package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankMvRepository
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPeriod
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ProductRankMvRepositoryImpl(
    private val weeklyRepository: ProductRankWeeklyReadJpaRepository,
    private val monthlyRepository: ProductRankMonthlyReadJpaRepository,
) : ProductRankMvRepository {
    override fun findTop100(
        period: RankingPeriod,
        baseDate: LocalDate,
    ): List<RankingEntry> {
        return when (period) {
            RankingPeriod.WEEKLY -> {
                weeklyRepository
                    .findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate)
                    .mapIndexed { index, rank ->
                        RankingEntry(
                            productId = rank.productId,
                            rank = index.toLong() + 1,
                            score = rank.rankingScore,
                        )
                    }
            }

            RankingPeriod.MONTHLY -> {
                monthlyRepository
                    .findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate)
                    .mapIndexed { index, rank ->
                        RankingEntry(
                            productId = rank.productId,
                            rank = index.toLong() + 1,
                            score = rank.rankingScore,
                        )
                    }
            }

            RankingPeriod.DAILY -> error("Daily ranking does not use RDB MV.")
        }
    }
}
