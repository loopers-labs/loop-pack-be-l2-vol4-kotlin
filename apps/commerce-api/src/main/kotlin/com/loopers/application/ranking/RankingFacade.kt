package com.loopers.application.ranking

import com.loopers.application.ranking.dto.RankingProductInfo
import com.loopers.application.ranking.dto.RankingQuery
import com.loopers.domain.ranking.RankingProductQueryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class RankingFacade(
    private val rankingQueryService: RankingQueryService,
    private val rankingProductQueryRepository: RankingProductQueryRepository,
) {
    fun getRankings(query: RankingQuery): Page<RankingProductInfo> {
        val rankingPage = rankingQueryService.getPage(
            period = query.period,
            date = query.date,
            page = query.page,
            size = query.size,
        )
        val productById = rankingProductQueryRepository
            .findDisplayableSummaries(rankingPage.entries.map { it.productId })
            .associateBy { it.productId }
        val content = rankingPage.entries.mapNotNull { entry ->
            productById[entry.productId]?.let { product -> RankingProductInfo.from(entry, product) }
        }

        return PageImpl(
            content,
            PageRequest.of(query.page, query.size),
            rankingPage.totalElements,
        )
    }
}
