package com.loopers.metrics.application

import com.fasterxml.jackson.databind.JsonNode
import com.loopers.metrics.domain.EventHandled
import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.ProductMetricsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductMetricsService(
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricsRepository: ProductMetricsRepository,
) {
    private val logger = LoggerFactory.getLogger(ProductMetricsService::class.java)

    @Transactional
    fun handle(eventId: String, eventType: String, payload: JsonNode) {
        if (eventHandledRepository.exists(eventId)) {
            return
        }
        when (eventType) {
            "ProductLikedEvent" ->
                productMetricsRepository.upsertDelta(requiredLong(payload, "productId", eventId) ?: return, likeDelta = 1)
            "ProductUnlikedEvent" ->
                productMetricsRepository.upsertDelta(requiredLong(payload, "productId", eventId) ?: return, likeDelta = -1)
            "ProductViewedEvent" ->
                productMetricsRepository.upsertDelta(requiredLong(payload, "productId", eventId) ?: return, viewDelta = 1)
            "OrderCreatedEvent" -> (payload["items"] ?: return skipMissingField("items", eventId)).forEach {
                productMetricsRepository.upsertDelta(it["productId"].asLong(), salesDelta = it["quantity"].asLong())
            }
            else -> {
                logger.warn("알 수 없는 eventType — skip (eventType={}, eventId={})", eventType, eventId)
                return
            }
        }
        eventHandledRepository.save(EventHandled(eventId))
    }

    // 프로듀서 계약상 항상 존재하는 필드 — 누락은 이상 payload 신호이므로 NPE 로 배치 전체를 재전달시키지 않고 warn + skip 만 한다.
    private fun requiredLong(payload: JsonNode, field: String, eventId: String): Long? {
        val node = payload[field]
        if (node == null) {
            skipMissingField(field, eventId)
            return null
        }
        return node.asLong()
    }

    private fun skipMissingField(field: String, eventId: String) {
        logger.warn("{} 없는 payload — skip (eventId={})", field, eventId)
    }
}
