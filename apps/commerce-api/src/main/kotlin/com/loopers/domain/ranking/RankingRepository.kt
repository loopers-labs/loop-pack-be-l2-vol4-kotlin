package com.loopers.domain.ranking

import org.springframework.data.domain.Pageable
import java.time.LocalDate

interface RankingRepository {
    fun findPage(date: LocalDate, pageable: Pageable): RankingPage

    fun findRank(date: LocalDate, productId: Long): Long?
}
