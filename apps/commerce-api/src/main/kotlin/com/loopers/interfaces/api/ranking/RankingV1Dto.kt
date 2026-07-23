package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingItemResult
import com.loopers.application.ranking.RankingPageResult
import java.time.format.DateTimeFormatter

class RankingV1Dto {
    data class RankingPageResponse(
        val date: String,
        val period: String,
        /** 주간/월간일 때 실제 집계 구간(yyyyMMdd). DAILY는 null. */
        val periodStart: String?,
        val periodEnd: String?,
        val page: Int,
        val size: Int,
        val totalCount: Long,
        val items: List<RankingItemResponse>,
    ) {
        companion object {
            fun from(result: RankingPageResult): RankingPageResponse = RankingPageResponse(
                date = result.date.format(DateTimeFormatter.BASIC_ISO_DATE),
                period = result.period.name,
                periodStart = result.periodStart?.format(DateTimeFormatter.BASIC_ISO_DATE),
                periodEnd = result.periodEnd?.format(DateTimeFormatter.BASIC_ISO_DATE),
                page = result.page,
                size = result.size,
                totalCount = result.totalCount,
                items = result.items.map(RankingItemResponse::from),
            )
        }
    }

    data class RankingItemResponse(
        val rank: Long,
        val productId: Long,
        val score: Double,
        val productName: String?,
        val price: Long?,
    ) {
        companion object {
            fun from(item: RankingItemResult): RankingItemResponse = RankingItemResponse(
                rank = item.rank,
                productId = item.productId,
                score = item.score,
                productName = item.productName,
                price = item.price,
            )
        }
    }
}
