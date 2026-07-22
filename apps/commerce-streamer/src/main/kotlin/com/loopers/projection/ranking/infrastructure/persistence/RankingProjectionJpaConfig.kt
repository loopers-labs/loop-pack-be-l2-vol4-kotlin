package com.loopers.projection.ranking.infrastructure.persistence

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(
    basePackageClasses = [
        ProductRankingDailyJpaRepository::class,
    ],
)
class RankingProjectionJpaConfig
