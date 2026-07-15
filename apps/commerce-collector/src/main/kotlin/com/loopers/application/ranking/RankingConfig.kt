package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingEventInboxRepositoryPort
import com.loopers.domain.ranking.RankingRepositoryPort
import com.loopers.domain.ranking.RankingService
import com.loopers.domain.ranking.RankingWeightBoardsPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RankingConfig {
    @Bean
    fun rankingService(
        rankingRepositoryPort: RankingRepositoryPort,
        rankingEventInboxRepositoryPort: RankingEventInboxRepositoryPort,
        rankingWeightBoardsPort: RankingWeightBoardsPort,
    ): RankingService = RankingService(rankingRepositoryPort, rankingEventInboxRepositoryPort, rankingWeightBoardsPort)
}
