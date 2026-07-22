package com.loopers.projection.ranking.scheduler

import com.loopers.projection.ranking.application.RankingRdbSyncService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "commerce-ranking.rdb-sync",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RankingRdbSyncScheduler(
    private val rankingRdbSyncService: RankingRdbSyncService,
) {
    @Scheduled(
        fixedDelayString = "\${commerce-ranking.rdb-sync.fixed-delay-ms:600000}",
        initialDelayString = "\${commerce-ranking.rdb-sync.fixed-delay-ms:600000}",
    )
    fun sync() {
        rankingRdbSyncService.sync()
    }
}
