package com.loopers.interfaces.consumer

import com.loopers.eventstore.application.EventStoreAppender
import com.loopers.metrics.application.ProductMetricsService
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ProductMetricsKafkaConsumerTest {
    private val productMetricsService: ProductMetricsService = mock()
    private val eventStoreAppender: EventStoreAppender = mock()
    private val consumer = ProductMetricsKafkaConsumer(productMetricsService, eventStoreAppender)
    private val acknowledgment: org.springframework.kafka.support.Acknowledgment = mock()

    private fun record(topic: String, json: String): ConsumerRecord<String, ByteArray> =
        ConsumerRecord(topic, 0, 0, "1", json.toByteArray())

    @DisplayName("product-events 의 좋아요/취소 레코드를 타입으로 역직렬화해 서비스에 위임하고, 전부 처리한 뒤 ack 한다.")
    @Test
    fun delegatesTypedProductEventsAndAcknowledges() {
        val liked = """{"eventId":"e-1","eventType":"ProductLikedEvent","productId":1,"userId":7}"""
        val unliked = """{"eventId":"e-2","eventType":"ProductUnlikedEvent","productId":1,"userId":7}"""

        consumer.consumeProductEvents(listOf(record("product-events", liked), record("product-events", unliked)), acknowledgment)

        assertAll(
            { verify(productMetricsService).handle(eq(ProductEvent.Liked("e-1", 1))) },
            { verify(productMetricsService).handle(eq(ProductEvent.Unliked("e-2", 1))) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("order-events 레코드는 items 의 상품·수량까지 타입으로 역직렬화해 위임한다 — unitPrice 등 계약 외 필드는 무시.")
    @Test
    fun delegatesTypedOrderCreatedEvent() {
        val json = """{"eventId":"e-1","eventType":"OrderCreatedEvent","orderId":10,
            "items":[{"productId":1,"quantity":2,"unitPrice":100},{"productId":2,"quantity":3,"unitPrice":50}]}"""

        consumer.consumeOrderEvents(listOf(record("order-events", json)), acknowledgment)

        val expected = OrderCreatedEvent(
            eventId = "e-1",
            items = listOf(OrderCreatedEvent.OrderLine(1, 2), OrderCreatedEvent.OrderLine(2, 3)),
        )
        assertAll(
            { verify(productMetricsService).handle(eq(expected)) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("user-action-events 의 조회 레코드를 타입으로 역직렬화해 위임한다.")
    @Test
    fun delegatesTypedViewedEvent() {
        val json = """{"eventId":"e-1","eventType":"ProductViewedEvent","productId":1}"""

        consumer.consumeUserActionEvents(listOf(record("user-action-events", json)), acknowledgment)

        verify(productMetricsService).handle(eq(ProductViewedEvent("e-1", 1)))
    }

    @DisplayName("토픽 계약에 없는 eventType 은 건너뛰고 나머지를 처리한다.")
    @Test
    fun skipsUnknownEventTypeAndProcessesRest() {
        val unknown = """{"eventId":"e-1","eventType":"ProductRestockedEvent","productId":1}"""
        val liked = """{"eventId":"e-2","eventType":"ProductLikedEvent","productId":1}"""

        consumer.consumeProductEvents(listOf(record("product-events", unknown), record("product-events", liked)), acknowledgment)

        assertAll(
            { verify(productMetricsService).handle(eq(ProductEvent.Liked("e-2", 1))) },
            { verify(productMetricsService, never()).handle(eq(ProductEvent.Liked("e-1", 1))) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("파싱 불가 레코드는 건너뛰고 나머지를 처리한다.")
    @Test
    fun skipsMalformedRecordAndProcessesRest() {
        val valid = """{"eventId":"e-2","eventType":"ProductViewedEvent","productId":1}"""

        consumer.consumeUserActionEvents(
            listOf(record("user-action-events", "not-json"), record("user-action-events", valid)),
            acknowledgment,
        )

        assertAll(
            { verify(productMetricsService).handle(eq(ProductViewedEvent("e-2", 1))) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("필수 필드(eventId·productId)가 빠진 레코드는 서비스에 위임하지 않고 건너뛴다.")
    @Test
    fun skipsRecordMissingRequiredFields() {
        val withoutEventId = """{"eventType":"ProductViewedEvent","productId":1}"""
        val withoutProductId = """{"eventId":"e-1","eventType":"ProductViewedEvent"}"""

        consumer.consumeUserActionEvents(
            listOf(record("user-action-events", withoutEventId), record("user-action-events", withoutProductId)),
            acknowledgment,
        )

        assertAll(
            { verifyNoInteractions(productMetricsService) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("서비스 처리 중 예외가 나면 전파하고 ack 하지 않는다 — 배치 재전달로 이어진다.")
    @Test
    fun propagatesAndSkipsAck_whenServiceFails() {
        doThrow(RuntimeException("db down")).whenever(productMetricsService).handle(any<ProductEvent>())

        assertThrows<RuntimeException> {
            consumer.consumeProductEvents(
                listOf(record("product-events", """{"eventId":"e-1","eventType":"ProductLikedEvent","productId":1}""")),
                acknowledgment,
            )
        }
        verify(acknowledgment, never()).acknowledge()
    }
}
