package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankPublicationRepository
import com.loopers.domain.ranking.PublishedRanking
import com.loopers.domain.ranking.RankingPeriod
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ProductRankPublicationRepositoryImpl(
    private val productRankPublicationJpaRepository: ProductRankPublicationJpaRepository,
) : ProductRankPublicationRepository {
    override fun findLatestPublished(
        period: RankingPeriod,
        baseDate: LocalDate,
    ): PublishedRanking? {
        return productRankPublicationJpaRepository
            .findFirstByPeriodAndBaseDateLessThanEqualOrderByBaseDateDesc(period.name, baseDate)
            ?.let {
                PublishedRanking(
                    period = period,
                    baseDate = it.baseDate,
                    generationId = it.generationId,
                )
            }
    }
}
