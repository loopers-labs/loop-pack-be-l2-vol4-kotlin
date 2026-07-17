package com.loopers.projection.ranking.scheduler

import com.loopers.projection.ranking.application.RankingCarryOverService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "commerce-ranking.carry-over",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RankingCarryOverScheduler(
    private val rankingCarryOverService: RankingCarryOverService,
) {
    @Scheduled(cron = "\${commerce-ranking.carry-over.cron:0 55 23 * * *}", zone = "Asia/Seoul")
    fun carryOver() {
        rankingCarryOverService.carryOver()
    }
}
