package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.ProductMetricsFacade
import com.loopers.application.metrics.SalesLine
import com.loopers.kafka.EventEnvelope
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class MetricsEventHandlerTest {
    private val facade = mockk<ProductMetricsFacade>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val handler = MetricsEventHandler(facade, objectMapper)

    private fun envelope(eventType: String, aggregateId: String, eventId: UUID, payload: String = "{}") =
        EventEnvelope(
            eventId = eventId.toString(),
            eventType = eventType,
            aggregateType = if (eventType == "ORDER_CREATED") "ORDER" else "PRODUCT",
            aggregateId = aggregateId,
            occurredAt = LocalDateTime.now(),
            payload = objectMapper.readTree(payload),
        )

    @Test
    fun `LIKE_CREATED 는 좋아요 증가로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("LIKE_CREATED", aggregateId = "7", eventId = eventId))

        verify { facade.increaseLike(eventId, 7L) }
    }

    @Test
    fun `LIKE_CANCELED 는 좋아요 감소로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("LIKE_CANCELED", aggregateId = "7", eventId = eventId))

        verify { facade.decreaseLike(eventId, 7L) }
    }

    @Test
    fun `PRODUCT_VIEWED 는 조회 증가로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("PRODUCT_VIEWED", aggregateId = "7", eventId = eventId))

        verify { facade.increaseView(eventId, 7L) }
    }

    @Test
    fun `ORDER_CREATED 는 payload 의 라인을 판매량 누적으로 번역된다`() {
        val eventId = UUID.randomUUID()
        val payload = """{"lines":[{"productId":7,"quantity":2},{"productId":9,"quantity":1}]}"""

        handler.handle(envelope("ORDER_CREATED", aggregateId = "100", eventId = eventId, payload = payload))

        verify {
            facade.addSales(eventId, listOf(SalesLine(7L, 2), SalesLine(9L, 1)))
        }
    }

    @Test
    fun `역직렬화할 수 없는 메시지는 집계 없이 건너뛴다 - 재전달 반복으로 파티션이 막히지 않는다`() {
        handler.handle("not-json".toByteArray())

        verify(exactly = 0) { facade.increaseLike(any(), any()) }
        verify(exactly = 0) { facade.decreaseLike(any(), any()) }
        verify(exactly = 0) { facade.increaseView(any(), any()) }
        verify(exactly = 0) { facade.addSales(any(), any()) }
    }

    @Test
    fun `aggregateId 가 상품 식별자가 아니면 집계 없이 건너뛴다`() {
        handler.handle(envelope("LIKE_CREATED", aggregateId = "not-a-number", eventId = UUID.randomUUID()))

        verify(exactly = 0) { facade.increaseLike(any(), any()) }
    }

    @Test
    fun `ORDER_CREATED payload 에 lines 배열이 없으면 집계 없이 건너뛴다`() {
        handler.handle(envelope("ORDER_CREATED", aggregateId = "100", eventId = UUID.randomUUID(), payload = "{}"))

        verify(exactly = 0) { facade.addSales(any(), any()) }
    }
}
