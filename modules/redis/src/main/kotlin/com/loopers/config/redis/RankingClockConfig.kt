package com.loopers.config.redis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class RankingClockConfig {
    companion object {
        const val RANKING_CLOCK = "rankingClock"
    }

    @Bean(RANKING_CLOCK)
    fun rankingClock(): Clock = Clock.systemUTC()
}
