package com.loopers.application.ranking

import java.time.LocalDate

data class RankingPageResult(
    val date: LocalDate,
    val page: Int,
    val size: Int,
    val totalCount: Long,
    val items: List<RankingItemResult>,
    val period: RankingPageCommand.PeriodType = RankingPageCommand.PeriodType.DAILY,
    /** 주간/월간일 때 실제 집계 구간. DAILY는 null. */
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
)

data class RankingItemResult(
    val rank: Long,
    val productId: Long,
    val score: Double,
    /** 상품이 조회되지 않으면(삭제 등) null. */
    val productName: String?,
    val price: Long?,
)
