package com.loopers.projection.ranking.application

import com.loopers.projection.ranking.port.ProductRankingStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RankingCarryOverService(
    private val productRankingStore: ProductRankingStore,
    private val properties: RankingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun carryOver() {
        val today = LocalDate.now(RankingKey.ZONE)
        val applied = productRankingStore.carryOver(
            from = today,
            to = today.plusDays(1),
            decay = properties.carryOver.decay,
            minScore = properties.carryOver.minScore,
        )
        if (!applied) {
            log.info("랭킹 carry-over 를 건너뜁니다. (당일 랭킹판 없음 또는 이미 수행됨) date={}", today)
        }
    }
}
