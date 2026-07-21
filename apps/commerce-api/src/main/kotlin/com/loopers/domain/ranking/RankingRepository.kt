package com.loopers.domain.ranking

import org.springframework.data.domain.Pageable
import java.time.LocalDate

interface RankingRepository {
    fun findPage(period: RankingPeriod, date: LocalDate, pageable: Pageable): RankingPage

    fun findRank(period: RankingPeriod, date: LocalDate, productId: Long): Long?
}
