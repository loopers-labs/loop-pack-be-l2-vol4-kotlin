package com.loopers.domain.ranking

import java.time.LocalDate

/**
 * 랭킹 조회 Domain Service. Redis ZSET이 아는 것은 productId/score뿐이며,
 * 상품 정보 병합(hydration)은 application 계층의 책임이다 — 이 서비스는 Product를 모른다.
 */
class RankingService(
    private val rankingRepositoryPort: RankingRepositoryPort,
) {
    fun getPage(date: LocalDate, page: Int, size: Int): RankingPage {
        val board = RankingBoard.allOf(date)
        val offset = (page - 1L) * size
        val entries = rankingRepositoryPort.getPage(board, offset, size.toLong())
        val totalCount = rankingRepositoryPort.getTotalCount(board)
        return RankingPage(date = date, page = page, size = size, totalCount = totalCount, entries = entries)
    }

    fun getProductRanking(date: LocalDate, productId: Long): RankingEntry? =
        rankingRepositoryPort.getEntry(RankingBoard.allOf(date), productId)
}
