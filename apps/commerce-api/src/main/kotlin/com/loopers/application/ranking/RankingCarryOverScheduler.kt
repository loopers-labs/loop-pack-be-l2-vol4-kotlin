package com.loopers.application.ranking

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RankingCarryOverScheduler(
    private val service: RankingCarryOverService,
) {
    @Scheduled(
        cron = "\${commerce.ranking.carry-over.cron:0 50 23 * * *}",
        zone = "\${commerce.ranking.carry-over.zone:Asia/Seoul}",
    )
    fun carryOver() {
        val count = service.carryToday()
        log.info("Ranking carry-over completed. count={}", count)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RankingCarryOverScheduler::class.java)
    }
}
