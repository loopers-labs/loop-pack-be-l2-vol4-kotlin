package com.loopers.domain.ranking

import java.time.LocalDate

/**
 * 주간/월간 랭킹 조회 Domain Service. 기준일을 직전 완결 기간으로 해석해 MV 스냅샷을 서빙한다.
 * 일간(RankingService)과 달리 이월/가중치 버전 개념이 없다 — 배치가 확정한 스냅샷을 그대로 읽는다.
 * 스냅샷이 없으면(배치 미실행) 빈 페이지를 반환한다. 상품 정보 hydration은 application 계층의 책임.
 */
class PeriodRankingService(
    private val periodRankingRepositoryPort: PeriodRankingRepositoryPort,
) {
    fun getPage(period: RankingPeriod, date: LocalDate, page: Int, size: Int): RankingPage {
        val aggregatedDate = period.aggregatedDateFor(date)
        val entries = periodRankingRepositoryPort.getPage(period, aggregatedDate, page, size)
        val totalCount = periodRankingRepositoryPort.getTotalCount(period, aggregatedDate)
        return RankingPage(date = date, page = page, size = size, totalCount = totalCount, entries = entries)
    }
}
