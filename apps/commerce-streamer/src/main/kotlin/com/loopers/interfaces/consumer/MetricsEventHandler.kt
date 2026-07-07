package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.ProductMetricsFacade
import com.loopers.application.metrics.SalesLine
import com.loopers.kafka.EventEnvelope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Kafka 메시지를 도메인 의도로 번역해 집계 연산으로 넘긴다.
 * eventType 문자열과 와이어 포맷을 아는 것은 여기(interfaces)까지다 — application 컴포넌트는 이벤트 종류를 모르는 연산만 노출한다.
 * 멱등 키(eventId)를 그대로 전달해, 재소비 시에도 결과가 1회만 반영되도록 한다.
 *
 * 형식이 깨진 메시지(역직렬화 불가·필수 필드 누락)는 재전달해도 영영 실패하므로 기록만 남기고 건너뛴다 —
 * ack 없이 반복 재전달되면 파티션 전체가 막힌다. 처리(DB) 실패는 일시 장애일 수 있어 예외를 전파해 재전달로 복구한다.
 */
@Component
class MetricsEventHandler(
    private val productMetricsFacade: ProductMetricsFacade,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(MetricsEventHandler::class.java)

    fun handle(message: ByteArray) {
        val envelope = runCatching { objectMapper.readValue(message, EventEnvelope::class.java) }
            .getOrElse { e ->
                log.error("역직렬화할 수 없는 메시지 — 건너뛴다", e)
                return
            }
        handle(envelope)
    }

    fun handle(envelope: EventEnvelope) {
        val eventId = envelope.eventId.toUuidOrNull()
            ?: return skipMalformed(envelope, "eventId 가 UUID 형식이 아니다")
        when (envelope.eventType) {
            "LIKE_CREATED" -> productMetricsFacade.increaseLike(eventId, productIdOf(envelope) ?: return)
            "LIKE_CANCELED" -> productMetricsFacade.decreaseLike(eventId, productIdOf(envelope) ?: return)
            "PRODUCT_VIEWED" -> productMetricsFacade.increaseView(eventId, productIdOf(envelope) ?: return)
            "ORDER_CREATED" -> productMetricsFacade.addSales(eventId, salesLinesOf(envelope) ?: return)
            else -> Unit
        }
    }

    private fun productIdOf(envelope: EventEnvelope): Long? {
        val productId = envelope.aggregateId.toLongOrNull()
        if (productId == null) {
            skipMalformed(envelope, "aggregateId 가 상품 식별자가 아니다")
        }
        return productId
    }

    private fun salesLinesOf(envelope: EventEnvelope): List<SalesLine>? {
        val lines = envelope.payload.path("lines")
        if (!lines.isArray) {
            skipMalformed(envelope, "payload.lines 가 배열이 아니다")
            return null
        }
        return lines.map { line ->
            val productId = line.path("productId")
            val quantity = line.path("quantity")
            if (!productId.canConvertToLong() || !quantity.canConvertToInt()) {
                skipMalformed(envelope, "판매 라인의 productId/quantity 가 숫자가 아니다")
                return null
            }
            SalesLine(productId = productId.asLong(), quantity = quantity.asInt())
        }
    }

    private fun skipMalformed(envelope: EventEnvelope, reason: String) {
        log.error(
            "형식이 깨진 이벤트 — 건너뛴다: reason={} eventId={} eventType={} aggregateId={}",
            reason,
            envelope.eventId,
            envelope.eventType,
            envelope.aggregateId,
        )
    }

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
}
