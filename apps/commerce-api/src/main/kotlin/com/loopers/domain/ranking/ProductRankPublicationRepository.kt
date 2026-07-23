package com.loopers.domain.ranking

import java.time.LocalDate

interface ProductRankPublicationRepository {
    fun findLatestPublished(
        period: RankingPeriod,
        baseDate: LocalDate,
    ): PublishedRanking?
}
