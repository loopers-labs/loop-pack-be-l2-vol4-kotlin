package com.loopers.infrastructure.productmetric

import com.loopers.batch.job.productranking.ProductRankingBatchProperties
import com.loopers.domain.productmetric.ProductMetricMonthlyRepository
import com.loopers.domain.productmetric.ProductMetricWeeklyRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

@Import(MySqlTestContainersConfig::class)
@SpringBootTest
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
class ProductMetricPeriodRepositoryImplIntegrationTest @Autowired constructor(
    private val productMetricWeeklyRepository: ProductMetricWeeklyRepository,
    private val productMetricMonthlyRepository: ProductMetricMonthlyRepository,
    private val properties: ProductRankingBatchProperties,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("weekly/monthly metric은 baseDate와 productId unique key로 upsert된다")
    @Test
    fun upsertsPeriodMetricsByBaseDateAndProductId() {
        val weeklyBaseDate = LocalDate.parse("2026-08-03")
        val monthlyBaseDate = LocalDate.parse("2026-08-01")

        productMetricWeeklyRepository.upsert(weeklyBaseDate, 10L, 1L, 2L, 3L)
        productMetricWeeklyRepository.upsert(weeklyBaseDate, 10L, 4L, 5L, 6L)
        productMetricMonthlyRepository.upsert(monthlyBaseDate, 20L, 7L, 8L, 9L)
        productMetricMonthlyRepository.upsert(monthlyBaseDate, 20L, 10L, 11L, 12L)

        val weekly = productMetricWeeklyRepository.find(weeklyBaseDate, 10L)
        val monthly = productMetricMonthlyRepository.find(monthlyBaseDate, 20L)
        assertAll(
            { assertThat(weekly?.viewCount).isEqualTo(4L) },
            { assertThat(weekly?.likeCount).isEqualTo(5L) },
            { assertThat(weekly?.salesAmount).isEqualTo(6L) },
            { assertThat(monthly?.viewCount).isEqualTo(10L) },
            { assertThat(monthly?.likeCount).isEqualTo(11L) },
            { assertThat(monthly?.salesAmount).isEqualTo(12L) },
        )
    }

    @DisplayName("product ranking batch 기본 fetch/chunk/cache TTL 설정을 사용한다")
    @Test
    fun bindsProductRankingBatchProperties() {
        assertAll(
            { assertThat(properties.metric.fetchSize).isEqualTo(1_000) },
            { assertThat(properties.metric.chunkSize).isEqualTo(500) },
            { assertThat(properties.cache.weeklyTtlDays).isEqualTo(8L) },
            { assertThat(properties.cache.monthlyTtlDays).isEqualTo(32L) },
        )
    }
}
