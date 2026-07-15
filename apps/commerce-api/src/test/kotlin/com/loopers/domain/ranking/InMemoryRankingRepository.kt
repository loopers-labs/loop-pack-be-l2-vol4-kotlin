package com.loopers.domain.ranking

import org.springframework.data.domain.Pageable
import java.time.LocalDate

/**
 * RankingFacade 단위 테스트용 in-memory 가짜.
 * ZSET 정렬/페이징은 통합 테스트가 검증하므로, 여기선 미리 세팅한 entries 를 그대로 돌려준다.
 */
class InMemoryRankingRepository : RankingRepository {
    val entries = mutableListOf<RankingEntry>()

    override fun findPage(date: LocalDate, pageable: Pageable): RankingPage =
        RankingPage(entries.toList(), entries.size.toLong())

    override fun findRank(date: LocalDate, productId: Long): Long? =
        entries.firstOrNull { it.productId == productId }?.rank
}
