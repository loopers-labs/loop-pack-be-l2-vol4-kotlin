package com.loopers.application.metrics

import com.loopers.infrastructure.eventhandled.EventHandledJpaEntity
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MetricsEventProcessor(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(event: IncomingEvent): Boolean {
        if (isAlreadyHandled(event.eventId)) {
            log.info("이미 처리된 이벤트: eventId={}", event.eventId)
            return false
        }

        when (event.eventType) {
            "ProductLikeMetricIncreased", "ProductLiked", "LIKE" -> handleLikeEvent(event)
            "ProductLikeMetricDecreased", "ProductUnliked", "UNLIKE" -> handleUnlikeEvent(event)
            "PAYMENT_SUCCESS" -> handlePaymentSuccessEvent(event)
            "PRODUCT_VIEWED" -> handleProductViewedEvent(event)
            else -> {
                log.warn("알 수 없는 이벤트 타입: eventType={}", event.eventType)
                return false
            }
        }

        markHandled(event.eventId, event.eventType)
        return true
    }

    private fun handleLikeEvent(event: IncomingEvent) {
        val productId = event.payload["productId"]?.toString()?.toLong() ?: return
        productMetricsJpaRepository.upsertLikeCount(productId, 1)
    }

    private fun handleUnlikeEvent(event: IncomingEvent) {
        val productId = event.payload["productId"]?.toString()?.toLong() ?: return
        productMetricsJpaRepository.upsertLikeCount(productId, -1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handlePaymentSuccessEvent(event: IncomingEvent) {
        val items = event.payload["items"] as? List<Map<String, Any>> ?: return
        items.forEach { item ->
            val productId = item["productId"]?.toString()?.toLong() ?: return@forEach
            val quantity = item["quantity"]?.toString()?.toInt() ?: return@forEach
            val amount = item["amount"]?.toString()?.toLong() ?: return@forEach
            productMetricsJpaRepository.upsertOrderMetrics(productId, quantity.toLong(), amount)
        }
    }

    private fun handleProductViewedEvent(event: IncomingEvent) {
        val productId = event.payload["productId"]?.toString()?.toLong() ?: return
        productMetricsJpaRepository.upsertViewCount(productId, 1)
    }

    private fun isAlreadyHandled(eventId: String): Boolean {
        return eventHandledJpaRepository.existsById(eventId)
    }

    private fun markHandled(eventId: String, eventType: String) {
        try {
            eventHandledJpaRepository.save(EventHandledJpaEntity(eventId = eventId, eventType = eventType))
        } catch (e: DataIntegrityViolationException) {
            log.info("이벤트 중복 처리 감지: eventId={}", eventId)
        }
    }
}

data class IncomingEvent(
    val eventId: String,
    val eventType: String,
    val payload: Map<String, Any>,
)
