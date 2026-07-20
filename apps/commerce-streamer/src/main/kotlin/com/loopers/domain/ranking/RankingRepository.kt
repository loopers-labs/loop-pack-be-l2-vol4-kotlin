package com.loopers.domain.ranking

interface RankingRepository {
    fun updateCatalog(command: CatalogRankingUpdate): RankingUpdateResult

    fun updateOrder(command: OrderRankingUpdate): RankingUpdateResult
}
