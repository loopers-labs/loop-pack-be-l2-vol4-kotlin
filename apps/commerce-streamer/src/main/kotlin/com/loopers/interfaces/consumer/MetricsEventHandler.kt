package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.ProductMetricsFacade
import com.loopers.application.metrics.SalesLine
import com.loopers.kafka.EventEnvelope
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Kafka 봉투를 도메인 의도로 번역해 집계 연산으로 넘긴다.
 * eventType 문자열을 아는 것은 여기(interfaces)까지다 — application 컴포넌트는 이벤트 종류를 모르는 연산만 노출한다.
 * 멱등 키(eventId)를 그대로 전달해, 재소비 시에도 결과가 1회만 반영되도록 한다.
 */
@Component
class MetricsEventHandler(
    private val productMetricsFacade: ProductMetricsFacade,
    private val objectMapper: ObjectMapper,
) {
    fun handle(envelope: EventEnvelope) {
        val eventId = UUID.fromString(envelope.eventId)
        when (envelope.eventType) {
            "LIKE_CREATED" -> productMetricsFacade.increaseLike(eventId, envelope.aggregateId.toLong())
            "LIKE_CANCELED" -> productMetricsFacade.decreaseLike(eventId, envelope.aggregateId.toLong())
            "PRODUCT_VIEWED" -> productMetricsFacade.increaseView(eventId, envelope.aggregateId.toLong())
            "ORDER_CREATED" -> productMetricsFacade.addSales(eventId, salesLinesOf(envelope))
            else -> Unit
        }
    }

    private fun salesLinesOf(envelope: EventEnvelope): List<SalesLine> =
        envelope.payload.path("lines").map { line ->
            SalesLine(productId = line.get("productId").asLong(), quantity = line.get("quantity").asInt())
        }
}
