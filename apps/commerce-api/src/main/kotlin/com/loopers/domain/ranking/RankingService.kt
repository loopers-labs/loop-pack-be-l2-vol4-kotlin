package com.loopers.domain.ranking

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RankingService(
    private val rankingRepository: RankingRepository,
) {
    fun getRankingPage(period: RankingPeriod, date: LocalDate, pageable: Pageable): RankingPage =
        rankingRepository.findPage(period, date, pageable)

    fun getRank(period: RankingPeriod, date: LocalDate, productId: Long): Long? =
        rankingRepository.findRank(period, date, productId)
}