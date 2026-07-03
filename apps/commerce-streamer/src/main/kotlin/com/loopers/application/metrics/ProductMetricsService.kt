package com.loopers.application.metrics

import com.loopers.domain.eventhandled.EventHandledModel
import com.loopers.domain.eventhandled.EventHandledRepository
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.interfaces.consumer.EventMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class ProductMetricsService(
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricsRepository: ProductMetricsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 멱등 처리 + 집계 반영을 한 트랜잭션으로 수행한다.
     * 이미 처리한 eventId 면 skip → At Least Once 재수신에도 결과는 한 번만 반영.
     */
    @Transactional
    fun handle(message: EventMessage) {
        if (eventHandledRepository.existsByEventId(message.eventId)) {
            return
        }
        eventHandledRepository.save(
            EventHandledModel(
                eventId = message.eventId,
                eventType = message.eventType,
                handledAt = ZonedDateTime.now(),
            ),
        )
        applyMetric(message)
    }

    private fun applyMetric(message: EventMessage) {
        when (message.eventType) {
            "ProductLiked" -> productMetricsRepository.upsert(message.aggregateId, likeDelta = 1, viewDelta = 0, salesDelta = 0)
            "ProductUnliked" -> productMetricsRepository.upsert(
                message.aggregateId,
                likeDelta = -1,
                viewDelta = 0,
                salesDelta = 0,
            )
            "ProductViewed" -> productMetricsRepository.upsert(message.aggregateId, likeDelta = 0, viewDelta = 1, salesDelta = 0)
            "OrderCreated" -> {
                val items = message.payload.get("items") ?: return
                items.forEach { item ->
                    productMetricsRepository.upsert(
                        productId = item.get("productId").asLong(),
                        likeDelta = 0,
                        viewDelta = 0,
                        salesDelta = item.get("quantity").asLong(),
                    )
                }
            }
            else -> log.warn("알 수 없는 이벤트 타입, 무시: {}", message.eventType)
        }
    }
}
