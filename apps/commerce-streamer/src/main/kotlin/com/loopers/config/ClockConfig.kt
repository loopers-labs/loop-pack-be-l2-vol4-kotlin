package com.loopers.config

import com.loopers.ranking.domain.RankingKeys
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.system(RankingKeys.KST)
}
