package com.loopers.domain.ranking

import com.loopers.domain.product.dto.ProductSummary

interface RankingProductQueryRepository {
    fun findDisplayableSummaries(productIds: Collection<Long>): List<ProductSummary>
}
