package com.loopers.domain.ranking

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RankingService(
    private val rankingRepository: RankingRepository,
) {
    fun getRankingPage(date: LocalDate, pageable: Pageable): RankingPage =
        rankingRepository.findPage(date, pageable)

    fun getRank(date: LocalDate, productId: Long): Long? =
        rankingRepository.findRank(date, productId)
}
