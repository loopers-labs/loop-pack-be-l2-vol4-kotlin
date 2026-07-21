package com.loopers.infrastructure.productmetric

import com.loopers.domain.productmetric.ProductMetricDailyRepository
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
import java.time.LocalDate

@Import(MySqlTestContainersConfig::class)
@SpringBootTest
class ProductMetricDailyRepositoryImplIntegrationTest @Autowired constructor(
    private val productMetricDailyRepository: ProductMetricDailyRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("같은 날짜와 상품의 일별 metric을 unique key 기반 upsert로 원자 증분한다")
    @Test
    fun upsertsIncrementByMetricDateAndProductId() {
        val metricDate = LocalDate.parse("2026-07-02")

        productMetricDailyRepository.increment(
            metricDate = metricDate,
            productId = 10L,
            viewCountDelta = 1L,
            likeCountDelta = 1L,
            salesAmountDelta = 1_000L,
        )
        productMetricDailyRepository.increment(
            metricDate = metricDate,
            productId = 10L,
            viewCountDelta = 2L,
            likeCountDelta = -1L,
            salesAmountDelta = 2_000L,
        )

        val metric = productMetricDailyRepository.find(metricDate = metricDate, productId = 10L)
        assertAll(
            { assertThat(metric?.viewCount).isEqualTo(3L) },
            { assertThat(metric?.likeCount).isEqualTo(0L) },
            { assertThat(metric?.salesAmount).isEqualTo(3_000L) },
        )
    }
}
