package com.loopers.application.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest
class RankingCarryOverSchedulerContextTest @Autowired constructor(
    private val applicationContext: ApplicationContext,
) {
    @DisplayName("API application context에는 daily carry-over scheduler bean이 존재하지 않는다")
    @Test
    fun doesNotRegisterCarryOverScheduler() {
        assertThat(applicationContext.beanDefinitionNames).doesNotContain("rankingCarryOverScheduler")
    }
}
