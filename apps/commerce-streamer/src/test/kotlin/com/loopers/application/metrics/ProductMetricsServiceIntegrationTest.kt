package com.loopers.application.metrics

import com.loopers.domain.metrics.ProductMetricRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductMetricsServiceIntegrationTest {
    @Autowired lateinit var service: ProductMetricsService

    @Autowired lateinit var metricRepository: ProductMetricRepository

    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("신규 eventId면 좋아요 수를 upsert(신규 행 생성)한다.")
    @Test
    fun appliesLikeOnce() {
        service.applyOnce("evt-1", listOf(MetricDelta(productId = 10L, like = 1)))
        assertThat(metricRepository.findByProductId(10L)?.likeCount).isEqualTo(1L)
    }

    @DisplayName("같은 eventId를 두 번 적용해도 결과는 1회만 반영된다(멱등).")
    @Test
    fun idempotentOnDuplicateEventId() {
        service.applyOnce("evt-2", listOf(MetricDelta(productId = 10L, like = 1)))
        service.applyOnce("evt-2", listOf(MetricDelta(productId = 10L, like = 1)))
        assertThat(metricRepository.findByProductId(10L)?.likeCount).isEqualTo(1L)
    }

    @DisplayName("판매 이벤트는 상품별 sales_count에 수량만큼 누적된다.")
    @Test
    fun accumulatesSales() {
        service.applyOnce("evt-3", listOf(MetricDelta(productId = 10L, sales = 3)))
        service.applyOnce("evt-4", listOf(MetricDelta(productId = 10L, sales = 2)))
        assertThat(metricRepository.findByProductId(10L)?.salesCount).isEqualTo(5L)
    }
}
