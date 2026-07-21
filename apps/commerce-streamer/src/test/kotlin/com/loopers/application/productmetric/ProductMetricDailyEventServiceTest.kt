package com.loopers.application.productmetric

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.productmetric.ProductMetricDaily
import com.loopers.domain.productmetric.ProductMetricDailyRepository
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.OrderEventItemMessage
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate
import java.time.ZonedDateTime

class ProductMetricDailyEventServiceTest {
    @DisplayName("Catalog view, like, unlike 이벤트를 KST 날짜와 상품별 일별 metric으로 증분한다")
    @Test
    fun incrementsCatalogMetricByKstDateAndProduct() {
        val fixture = Fixture()

        fixture.service.handle(
            catalogMessage(
                eventId = "view-1",
                eventType = CatalogEventType.PRODUCT_VIEWED,
                occurredAt = "2026-07-01T15:10:00Z",
            ),
        )
        fixture.service.handle(catalogMessage(eventId = "like-1", eventType = CatalogEventType.PRODUCT_LIKED))
        fixture.service.handle(catalogMessage(eventId = "unlike-1", eventType = CatalogEventType.PRODUCT_UNLIKED))

        val metric = fixture.productMetricDailyRepository.find(
            metricDate = LocalDate.parse("2026-07-02"),
            productId = 10L,
        )
        assertAll(
            { assertThat(metric?.viewCount).isEqualTo(1L) },
            { assertThat(metric?.likeCount).isEqualTo(0L) },
            { assertThat(metric?.salesAmount).isEqualTo(0L) },
        )
    }

    @DisplayName("결제 성공 이벤트의 item 금액을 상품별로 합산해 sales amount를 증분한다")
    @Test
    fun incrementsSalesAmountByProductForPaymentSucceeded() {
        val fixture = Fixture()
        val message = orderMessage(
            eventId = "payment-1",
            eventType = OrderEventType.PAYMENT_SUCCEEDED,
            items = listOf(
                OrderEventItemMessage(productId = 10L, quantity = 2L, unitPrice = 1_000L),
                OrderEventItemMessage(productId = 10L, quantity = 3L, unitPrice = 2_000L),
                OrderEventItemMessage(productId = 20L, quantity = 1L, unitPrice = 5_000L),
            ),
        )

        fixture.service.handle(message)

        assertAll(
            {
                assertThat(
                    fixture.productMetricDailyRepository.find(LocalDate.parse("2026-07-02"), 10L)?.salesAmount,
                ).isEqualTo(8_000L)
            },
            {
                assertThat(
                    fixture.productMetricDailyRepository.find(LocalDate.parse("2026-07-02"), 20L)?.salesAmount,
                ).isEqualTo(5_000L)
            },
        )
    }

    @DisplayName("같은 consumer group의 동일 eventId 재전달은 metric을 중복 증분하지 않는다")
    @Test
    fun skipsDuplicatedEventInSameConsumerGroup() {
        val fixture = Fixture()
        val message = catalogMessage(eventId = "event-1", eventType = CatalogEventType.PRODUCT_VIEWED)

        fixture.service.handle(message)
        fixture.service.handle(message)

        val metric = fixture.productMetricDailyRepository.find(LocalDate.parse("2026-07-02"), 10L)
        assertThat(metric?.viewCount).isEqualTo(1L)
    }

    @DisplayName("다른 consumer group의 동일 eventId 처리 이력은 Daily metric 처리에 영향을 주지 않는다")
    @Test
    fun ignoresHandledEventFromOtherConsumerGroup() {
        val fixture = Fixture()
        fixture.eventHandledRepository.save(
            EventHandled(
                consumerGroup = "loopers-default-consumer",
                eventId = "event-1",
                eventType = "PRODUCT_VIEWED",
            ),
        )

        fixture.service.handle(catalogMessage(eventId = "event-1", eventType = CatalogEventType.PRODUCT_VIEWED))

        val metric = fixture.productMetricDailyRepository.find(LocalDate.parse("2026-07-02"), 10L)
        assertAll(
            { assertThat(metric?.viewCount).isEqualTo(1L) },
            { assertThat(fixture.eventHandledRepository.exists("commerce-product-metric-daily", "event-1")).isTrue() },
        )
    }

    private class Fixture {
        val eventHandledRepository = FakeEventHandledRepository()
        val productMetricDailyRepository = FakeProductMetricDailyRepository()
        val service = ProductMetricDailyEventService(
            eventHandledRepository = eventHandledRepository,
            productMetricDailyRepository = productMetricDailyRepository,
        )
    }

    private class FakeEventHandledRepository : EventHandledRepository {
        private val events = mutableSetOf<Pair<String, String>>()

        override fun exists(
            consumerGroup: String,
            eventId: String,
        ): Boolean {
            return consumerGroup to eventId in events
        }

        override fun save(eventHandled: EventHandled): EventHandled {
            events.add(eventHandled.consumerGroup to eventHandled.eventId)
            return eventHandled
        }
    }

    private class FakeProductMetricDailyRepository : ProductMetricDailyRepository {
        private val metrics = mutableMapOf<Pair<LocalDate, Long>, ProductMetricDaily>()

        override fun increment(
            metricDate: LocalDate,
            productId: Long,
            viewCountDelta: Long,
            likeCountDelta: Long,
            salesAmountDelta: Long,
        ) {
            val current = metrics[metricDate to productId]
            metrics[metricDate to productId] = ProductMetricDaily(
                metricDate = metricDate,
                productId = productId,
                viewCount = (current?.viewCount ?: 0L) + viewCountDelta,
                likeCount = (current?.likeCount ?: 0L) + likeCountDelta,
                salesAmount = (current?.salesAmount ?: 0L) + salesAmountDelta,
            )
        }

        override fun find(
            metricDate: LocalDate,
            productId: Long,
        ): ProductMetricDaily? {
            return metrics[metricDate to productId]
        }
    }

    private fun catalogMessage(
        eventId: String,
        eventType: CatalogEventType,
        occurredAt: String = "2026-07-02T10:00:00+09:00",
    ): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = eventId,
            eventType = eventType,
            aggregateId = 10L,
            productId = 10L,
            brandId = 100L,
            memberId = 1L,
            version = 100L,
            occurredAt = ZonedDateTime.parse(occurredAt),
        )
    }

    private fun orderMessage(
        eventId: String,
        eventType: OrderEventType,
        items: List<OrderEventItemMessage>,
    ): OrderEventMessage {
        return OrderEventMessage(
            eventId = eventId,
            eventType = eventType,
            aggregateId = 20L,
            orderId = 20L,
            orderNumber = "order-20",
            memberId = 1L,
            paymentId = 30L,
            amount = 10_000L,
            items = items,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }
}
