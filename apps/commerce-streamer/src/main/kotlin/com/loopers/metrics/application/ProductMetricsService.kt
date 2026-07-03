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
            "ProductLikedEvent" -> productMetricsRepository.upsertDelta(payload["productId"].asLong(), likeDelta = 1)
            "ProductUnlikedEvent" -> productMetricsRepository.upsertDelta(payload["productId"].asLong(), likeDelta = -1)
            "ProductViewedEvent" -> productMetricsRepository.upsertDelta(payload["productId"].asLong(), viewDelta = 1)
            "OrderCreatedEvent" -> payload["items"].forEach {
                productMetricsRepository.upsertDelta(it["productId"].asLong(), salesDelta = it["quantity"].asLong())
            }
            else -> {
                logger.warn("알 수 없는 eventType — skip (eventType={}, eventId={})", eventType, eventId)
                return
            }
        }
        eventHandledRepository.save(EventHandled(eventId))
    }
}
