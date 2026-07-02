package com.loopers.application.metrics

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.infrastructure.metrics.ProcessedEventRepositoryImpl
import com.loopers.infrastructure.metrics.ProductMetricsRepositoryImpl
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    MySqlTestContainersConfig::class,
    DataSourceConfig::class,
    ProductMetricsRepositoryImpl::class,
    ProcessedEventRepositoryImpl::class,
    ProductMetricsFacade::class,
)
class ProductMetricsFacadeIntegrationTest @Autowired constructor(
    private val productMetricsFacade: ProductMetricsFacade,
    private val productMetricsRepository: ProductMetricsRepository,
) {

    @Test
    fun `좋아요 생성 이벤트는 상품 지표의 좋아요 수를 1 늘린다`() {
        productMetricsFacade.increaseLike(eventId = UUID.randomUUID(), productId = 1L)

        assertThat(productMetricsRepository.findByProductId(1L)!!.likeCount).isEqualTo(1L)
    }

    @Test
    fun `주문 생성 이벤트는 라인 수량만큼 판매량을 누적한다`() {
        productMetricsFacade.addSales(
            eventId = UUID.randomUUID(),
            lines = listOf(SalesLine(productId = 1L, quantity = 3), SalesLine(productId = 2L, quantity = 2)),
        )

        assertThat(productMetricsRepository.findByProductId(1L)!!.salesCount).isEqualTo(3L)
        assertThat(productMetricsRepository.findByProductId(2L)!!.salesCount).isEqualTo(2L)
    }

    @Test
    fun `조회 이벤트는 조회 수를 1 늘린다`() {
        productMetricsFacade.increaseView(eventId = UUID.randomUUID(), productId = 1L)

        assertThat(productMetricsRepository.findByProductId(1L)!!.viewCount).isEqualTo(1L)
    }

    @Test
    fun `같은 eventId 로 두 번 도착해도 결과는 1회만 반영된다`() {
        val eventId = UUID.randomUUID()

        productMetricsFacade.increaseLike(eventId = eventId, productId = 1L)
        productMetricsFacade.increaseLike(eventId = eventId, productId = 1L)

        assertThat(productMetricsRepository.findByProductId(1L)!!.likeCount).isEqualTo(1L)
    }
}
