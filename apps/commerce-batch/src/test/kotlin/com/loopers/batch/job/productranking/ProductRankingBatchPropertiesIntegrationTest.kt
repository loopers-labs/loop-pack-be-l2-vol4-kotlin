package com.loopers.batch.job.productranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class ProductRankingBatchPropertiesIntegrationTest {
    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)

    @DisplayName("product ranking batch 설정 기본값을 바인딩한다")
    @Test
    fun bindsProductRankingBatchProperties() {
        contextRunner.run { context ->
            val properties = context.getBean(ProductRankingBatchProperties::class.java)

            assertAll(
                { assertThat(properties.metric.fetchSize).isEqualTo(1_000) },
                { assertThat(properties.metric.chunkSize).isEqualTo(500) },
                { assertThat(properties.cache.weeklyTtlDays).isEqualTo(8) },
                { assertThat(properties.cache.monthlyTtlDays).isEqualTo(32) },
            )
        }
    }

    @Configuration
    @EnableConfigurationProperties(ProductRankingBatchProperties::class)
    private class TestConfig
}
