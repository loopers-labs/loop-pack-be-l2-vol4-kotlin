package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.ProductMetricsFacade
import com.loopers.application.metrics.SalesLine
import com.loopers.kafka.EventEnvelope
import com.loopers.kafka.MalformedEventException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Kafka 메시지를 도메인 의도로 번역해 집계 연산으로 넘긴다.
 * eventType 문자열과 와이어 포맷을 아는 것은 여기(interfaces)까지다 — application 컴포넌트는 이벤트 종류를 모르는 연산만 노출한다.
 * 멱등 키(eventId)를 그대로 전달해, 재소비 시에도 결과가 1회만 반영되도록 한다.
 *
 * 형식이 깨진 메시지(역직렬화 불가·필수 필드 누락)는 재전달해도 영영 실패하므로 MalformedEventException 으로 던진다 —
 * 에러 핸들러가 재시도 없이 DLT 로 격리해, 유실 없이 관측·재처리 가능하게 보관한다.
 * 처리(DB) 실패는 일시 장애일 수 있어 예외를 전파해 재전달로 복구한다.
 */
@Component
class MetricsEventHandler(
    private val productMetricsFacade: ProductMetricsFacade,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(MetricsEventHandler::class.java)

    fun handle(message: ByteArray) {
        val envelope = runCatching { objectMapper.readValue(message, EventEnvelope::class.java) }
            .getOrElse { e -> throw MalformedEventException("역직렬화할 수 없는 메시지", e) }
        handle(envelope)
    }

    fun handle(envelope: EventEnvelope) {
        val eventId = envelope.eventId.toUuidOrNull()
            ?: throw malformed(envelope, "eventId 가 UUID 형식이 아니다")
        when (envelope.eventType) {
            "LIKE_CREATED" -> productMetricsFacade.increaseLike(eventId, productIdOf(envelope))
            "LIKE_CANCELED" -> productMetricsFacade.decreaseLike(eventId, productIdOf(envelope))
            "PRODUCT_VIEWED" -> productMetricsFacade.increaseView(eventId, productIdOf(envelope))
            "ORDER_CREATED" -> productMetricsFacade.addSales(eventId, salesLinesOf(envelope))
            else -> log.debug("처리 대상이 아닌 이벤트 타입: {}", envelope.eventType)
        }
    }

    private fun productIdOf(envelope: EventEnvelope): Long =
        envelope.aggregateId.toLongOrNull()
            ?: throw malformed(envelope, "aggregateId 가 상품 식별자가 아니다")

    private fun salesLinesOf(envelope: EventEnvelope): List<SalesLine> {
        val lines = envelope.payload.path("lines")
        if (!lines.isArray) {
            throw malformed(envelope, "payload.lines 가 배열이 아니다")
        }
        return lines.map { line ->
            val productId = line.path("productId")
            val quantity = line.path("quantity")
            if (!productId.canConvertToLong() || !quantity.canConvertToInt()) {
                throw malformed(envelope, "판매 라인의 productId/quantity 가 숫자가 아니다")
            }
            SalesLine(productId = productId.asLong(), quantity = quantity.asInt())
        }
    }

    private fun malformed(envelope: EventEnvelope, reason: String): MalformedEventException =
        MalformedEventException(
            "$reason: eventId=${envelope.eventId} eventType=${envelope.eventType} aggregateId=${envelope.aggregateId}",
        )

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
