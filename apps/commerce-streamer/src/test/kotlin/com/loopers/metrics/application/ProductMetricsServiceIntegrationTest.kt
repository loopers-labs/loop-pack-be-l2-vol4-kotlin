package com.loopers.metrics.application

import com.loopers.metrics.infrastructure.EventHandledJpaRepository
import com.loopers.metrics.infrastructure.ProductMetricsJpaRepository
import com.loopers.support.DatabaseCleanup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

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

    private fun like(eventId: String, productId: Long) =
        productMetricsService.handle(ProductEvent.Liked(eventId, productId), Instant.now())

    private fun unlike(eventId: String, productId: Long) =
        productMetricsService.handle(ProductEvent.Unliked(eventId, productId), Instant.now())

    private fun view(eventId: String, productId: Long) =
        productMetricsService.handle(ProductViewedEvent(eventId, productId), Instant.now())

    private fun order(eventId: String, vararg items: OrderCreatedEvent.OrderLine) =
        productMetricsService.handle(OrderCreatedEvent(eventId, items.toList()), Instant.now())

    @DisplayName("처음 보는 상품의 좋아요 이벤트를 처리하면, product_metrics 행을 만들고 like_count 를 1 로 둔다.")
    @Test
    fun createsMetricsRow_whenFirstLikedEventHandled() {
        like("e-1", 1)

        val metrics = productMetricsJpaRepository.findAll()
        assertAll(
            { assertThat(metrics).hasSize(1) },
            { assertThat(metrics[0].productId).isEqualTo(1L) },
            { assertThat(metrics[0].likeCount).isEqualTo(1L) },
            { assertThat(metrics[0].salesCount).isEqualTo(0L) },
            { assertThat(metrics[0].viewCount).isEqualTo(0L) },
            { assertThat(eventHandledJpaRepository.existsById("e-1")).isTrue() },
        )
    }

    @DisplayName("같은 상품의 좋아요 이벤트를 두 번 처리하면, like_count 가 누적된다.")
    @Test
    fun accumulatesLikeCount_whenLikedTwice() {
        like("e-1", 1)
        like("e-2", 1)

        assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(2L)
    }

    @DisplayName("좋아요 취소 이벤트를 처리하면, like_count 가 감소한다.")
    @Test
    fun decreasesLikeCount_whenUnliked() {
        like("e-1", 1)
        unlike("e-2", 1)

        assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(0L)
    }

    @DisplayName("주문 생성 이벤트를 처리하면, items 의 상품별로 sales_count 에 수량을 더한다.")
    @Test
    fun accumulatesSalesPerProduct_whenOrderCreated() {
        order("e-1", OrderCreatedEvent.OrderLine(1, 2), OrderCreatedEvent.OrderLine(2, 3))

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
        view("e-1", 1)
        view("e-2", 1)

        assertThat(productMetricsJpaRepository.findAll().single().viewCount).isEqualTo(2L)
    }

    @DisplayName("같은 eventId 를 다시 처리하면, 집계를 반복하지 않는다 — event_handled 멱등.")
    @Test
    fun skipsDuplicateEventId() {
        like("e-1", 1)
        like("e-1", 1)

        assertAll(
            { assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(1L) },
            { assertThat(eventHandledJpaRepository.count()).isEqualTo(1L) },
        )
    }
}
