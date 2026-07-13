package com.loopers.domain.ranking

interface RankingProjectionRepository {
    fun projectCatalog(command: CatalogRankingProjection): RankingProjectionResult

    fun projectOrder(command: OrderRankingProjection): RankingProjectionResult
}
