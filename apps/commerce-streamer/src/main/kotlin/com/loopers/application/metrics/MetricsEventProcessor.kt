package com.loopers.application.metrics

import com.fasterxml.jackson.databind.ObjectMapper
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
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val LIKE_INCREASED = "ProductLikeMetricIncreased"
        private const val LIKE_DECREASED = "ProductLikeMetricDecreased"
        private const val PAYMENT_SUCCESS = "PAYMENT_SUCCESS"
        private const val PRODUCT_VIEWED = "PRODUCT_VIEWED"
    }

    @Transactional
    fun process(event: IncomingEvent): Boolean {
        if (isAlreadyHandled(event.eventId)) {
            log.info("이미 처리된 이벤트: eventId={}", event.eventId)
            return false
        }

        when (event.eventType) {
            LIKE_INCREASED -> handleLikeEvent(event, 1)
            LIKE_DECREASED -> handleLikeEvent(event, -1)
            PAYMENT_SUCCESS -> handlePaymentSuccessEvent(event)
            PRODUCT_VIEWED -> handleProductViewedEvent(event)
            else -> {
                log.warn("알 수 없는 이벤트 타입: eventType={}", event.eventType)
                return false
            }
        }

        markHandled(event.eventId, event.eventType)
        return true
    }

    private fun handleLikeEvent(event: IncomingEvent, delta: Long) {
        val payload = event.parsePayload<ProductMetricPayload>()
        productMetricsJpaRepository.upsertLikeCount(payload.productId, delta)
    }

    private fun handlePaymentSuccessEvent(event: IncomingEvent) {
        val payload = event.parsePayload<OrderMetricPayload>()
        payload.items.forEach { item ->
            productMetricsJpaRepository.upsertOrderMetrics(item.productId, item.quantity.toLong(), item.amount)
        }
    }

    private fun handleProductViewedEvent(event: IncomingEvent) {
        val payload = event.parsePayload<ProductMetricPayload>()
        productMetricsJpaRepository.upsertViewCount(payload.productId, 1)
    }

    private inline fun <reified T> IncomingEvent.parsePayload(): T {
        return objectMapper.convertValue(payload, T::class.java)
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

data class ProductMetricPayload(
    val productId: Long,
)

data class OrderMetricPayload(
    val items: List<OrderItemMetric>,
)

data class OrderItemMetric(
    val productId: Long,
    val quantity: Int,
    val amount: Long,
)
