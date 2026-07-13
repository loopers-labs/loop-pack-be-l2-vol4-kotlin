package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.CatalogRankingProjection
import com.loopers.domain.ranking.OrderRankingProjection
import com.loopers.domain.ranking.RankingProjectionRepository
import com.loopers.domain.ranking.RankingProjectionResult
import org.springframework.stereotype.Component

@Component
class RedisRankingProjectionRepository : RankingProjectionRepository {
    override fun projectCatalog(command: CatalogRankingProjection): RankingProjectionResult {
        return RankingProjectionResult.APPLIED
    }

    override fun projectOrder(command: OrderRankingProjection): RankingProjectionResult {
        return RankingProjectionResult.APPLIED
    }
}
