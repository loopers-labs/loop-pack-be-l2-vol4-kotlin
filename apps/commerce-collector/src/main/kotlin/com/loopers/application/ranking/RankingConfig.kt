package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingEventInboxRepositoryPort
import com.loopers.domain.ranking.RankingRepositoryPort
import com.loopers.domain.ranking.RankingService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RankingConfig {
    @Bean
    fun rankingService(
        rankingRepositoryPort: RankingRepositoryPort,
        rankingEventInboxRepositoryPort: RankingEventInboxRepositoryPort,
    ): RankingService = RankingService(rankingRepositoryPort, rankingEventInboxRepositoryPort)
}
