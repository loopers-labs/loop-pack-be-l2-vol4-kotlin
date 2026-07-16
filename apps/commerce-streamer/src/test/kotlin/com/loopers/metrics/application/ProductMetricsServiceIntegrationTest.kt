package com.loopers.metrics.application

import com.loopers.metrics.domain.EventSubscription
import com.loopers.metrics.infrastructure.EventHandledId
import com.loopers.metrics.infrastructure.EventHandledJpaRepository
import com.loopers.metrics.infrastructure.ProductMetricsJpaRepository
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import com.loopers.support.DatabaseCleanup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ProductMetricsServiceIntegrationTest @Autowired constructor(
    private val productMetricsService: ProductMetricsService,
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("처음 보는 상품의 좋아요 이벤트를 처리하면, product_metrics 행을 만들고 like_count 를 1 로 두고 METRICS 구독 처리 기록을 남긴다.")
    @Test
    fun createsMetricsRow_whenFirstLikedEventHandled() {
        productMetricsService.handle(ProductEvent.Liked("e-1", 1))

        val metrics = productMetricsJpaRepository.findAll()
        assertAll(
            { assertThat(metrics).hasSize(1) },
            { assertThat(metrics[0].productId).isEqualTo(1L) },
            { assertThat(metrics[0].likeCount).isEqualTo(1L) },
            { assertThat(metrics[0].salesCount).isEqualTo(0L) },
            { assertThat(metrics[0].viewCount).isEqualTo(0L) },
            { assertThat(eventHandledJpaRepository.existsById(EventHandledId("e-1", EventSubscription.METRICS))).isTrue() },
        )
    }

    @DisplayName("같은 상품의 좋아요 이벤트를 두 번 처리하면, like_count 가 누적된다.")
    @Test
    fun accumulatesLikeCount_whenLikedTwice() {
        productMetricsService.handle(ProductEvent.Liked("e-1", 1))
        productMetricsService.handle(ProductEvent.Liked("e-2", 1))

        assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(2L)
    }

    @DisplayName("좋아요 취소 이벤트를 처리하면, like_count 가 감소한다.")
    @Test
    fun decreasesLikeCount_whenUnliked() {
        productMetricsService.handle(ProductEvent.Liked("e-1", 1))
        productMetricsService.handle(ProductEvent.Unliked("e-2", 1))

        assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(0L)
    }

    @DisplayName("주문 생성 이벤트를 처리하면, items 의 상품별로 sales_count 에 수량을 더한다.")
    @Test
    fun accumulatesSalesPerProduct_whenOrderCreated() {
        productMetricsService.handle(
            OrderCreatedEvent("e-1", listOf(OrderCreatedEvent.OrderLine(1, 2), OrderCreatedEvent.OrderLine(2, 3))),
        )

        val salesByProductId = productMetricsJpaRepository.findAll().associate { it.productId to it.salesCount }
        assertAll(
            { assertThat(salesByProductId).hasSize(2) },
            { assertThat(salesByProductId[1L]).isEqualTo(2L) },
            { assertThat(salesByProductId[2L]).isEqualTo(3L) },
        )
    }

    @DisplayName("상품 조회 이벤트를 처리하면, view_count 가 증가한다.")
    @Test
    fun increasesViewCount_whenViewed() {
        productMetricsService.handle(ProductViewedEvent("e-1", 1))
        productMetricsService.handle(ProductViewedEvent("e-2", 1))

        assertThat(productMetricsJpaRepository.findAll().single().viewCount).isEqualTo(2L)
    }

    @DisplayName("같은 eventId 를 다시 처리하면, 집계를 반복하지 않는다 — METRICS 구독 멱등.")
    @Test
    fun skipsDuplicateEventId() {
        productMetricsService.handle(ProductEvent.Liked("e-1", 1))
        productMetricsService.handle(ProductEvent.Liked("e-1", 1))

        assertAll(
            { assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(1L) },
            { assertThat(eventHandledJpaRepository.count()).isEqualTo(1L) },
        )
    }
}
