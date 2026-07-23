package com.loopers.domain.ranking

import java.time.LocalDate

/** 주간/월간 랭킹 MV 조회 아웃바운드 포트. 배치가 만들어둔 TOP 100 스냅샷을 읽는다. */
interface PeriodRankingRepositoryPort {
    fun getPage(period: RankingPeriod, aggregatedDate: LocalDate, page: Int, size: Int): List<RankingEntry>

    fun getTotalCount(period: RankingPeriod, aggregatedDate: LocalDate): Long
}
