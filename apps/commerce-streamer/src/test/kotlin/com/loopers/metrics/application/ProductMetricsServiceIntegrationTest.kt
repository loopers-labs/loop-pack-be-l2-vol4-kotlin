package com.loopers.metrics.application

import com.fasterxml.jackson.databind.ObjectMapper
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

@SpringBootTest
@ActiveProfiles("test")
class ProductMetricsServiceIntegrationTest @Autowired constructor(
    private val productMetricsService: ProductMetricsService,
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    private fun handle(eventId: String, eventType: String, json: String) {
        productMetricsService.handle(eventId, eventType, objectMapper.readTree(json))
    }

    @DisplayName("처음 보는 상품의 좋아요 이벤트를 처리하면, product_metrics 행을 만들고 like_count 를 1 로 둔다.")
    @Test
    fun createsMetricsRow_whenFirstLikedEventHandled() {
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")

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
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")
        handle("e-2", "ProductLikedEvent", """{"productId":1}""")

        assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(2L)
    }

    @DisplayName("좋아요 취소 이벤트를 처리하면, like_count 가 감소한다.")
    @Test
    fun decreasesLikeCount_whenUnliked() {
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")
        handle("e-2", "ProductUnlikedEvent", """{"productId":1}""")

        assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(0L)
    }

    @DisplayName("주문 생성 이벤트를 처리하면, items 의 상품별로 sales_count 에 수량을 더한다.")
    @Test
    fun accumulatesSalesPerProduct_whenOrderCreated() {
        handle(
            "e-1",
            "OrderCreatedEvent",
            """{"orderId":10,"items":[{"productId":1,"quantity":2},{"productId":2,"quantity":3}]}""",
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
        handle("e-1", "ProductViewedEvent", """{"productId":1}""")
        handle("e-2", "ProductViewedEvent", """{"productId":1}""")

        assertThat(productMetricsJpaRepository.findAll().single().viewCount).isEqualTo(2L)
    }

    @DisplayName("같은 eventId 를 다시 처리하면, 집계를 반복하지 않는다 — event_handled 멱등.")
    @Test
    fun skipsDuplicateEventId() {
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")

        assertAll(
            { assertThat(productMetricsJpaRepository.findAll().single().likeCount).isEqualTo(1L) },
            { assertThat(eventHandledJpaRepository.count()).isEqualTo(1L) },
        )
    }

    @DisplayName("알 수 없는 eventType 은 예외 없이 무시하고, 집계와 event_handled 모두 기록하지 않는다.")
    @Test
    fun ignoresUnknownEventType() {
        handle("e-1", "UnknownEvent", """{"productId":1}""")

        assertAll(
            { assertThat(productMetricsJpaRepository.findAll()).isEmpty() },
            { assertThat(eventHandledJpaRepository.count()).isEqualTo(0L) },
        )
    }

    @DisplayName("productId 없는 payload 는 예외 없이 무시하고, 집계와 event_handled 모두 기록하지 않는다.")
    @Test
    fun ignoresPayloadWithoutProductId() {
        handle("e-1", "ProductLikedEvent", """{"other":1}""")

        assertAll(
            { assertThat(productMetricsJpaRepository.findAll()).isEmpty() },
            { assertThat(eventHandledJpaRepository.count()).isEqualTo(0L) },
        )
    }

    @DisplayName("items 없는 OrderCreatedEvent payload 는 예외 없이 무시하고, 집계와 event_handled 모두 기록하지 않는다.")
    @Test
    fun ignoresOrderPayloadWithoutItems() {
        handle("e-1", "OrderCreatedEvent", """{"orderId":10}""")

        assertAll(
            { assertThat(productMetricsJpaRepository.findAll()).isEmpty() },
            { assertThat(eventHandledJpaRepository.count()).isEqualTo(0L) },
        )
    }
}
