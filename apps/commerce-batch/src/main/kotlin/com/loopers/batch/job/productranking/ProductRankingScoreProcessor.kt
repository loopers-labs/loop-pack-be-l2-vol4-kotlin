package com.loopers.batch.job.productranking

import org.springframework.batch.item.ItemProcessor
import kotlin.math.ln

class ProductRankingScoreProcessor(
    private val viewWeight: Double,
    private val likeWeight: Double,
    private val salesWeight: Double,
) : ItemProcessor<ProductMetricAggregate, ProductRankingScore> {
    override fun process(item: ProductMetricAggregate): ProductRankingScore {
        return ProductRankingScore(
            baseDate = item.baseDate,
            productId = item.productId,
            rankingScore = item.viewCount * viewWeight +
                item.likeCount * likeWeight +
                ln(1.0 + item.salesAmount) * salesWeight,
        )
    }
}
