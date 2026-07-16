package com.loopers.metrics.application

import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.ProductMetricsRepository
import com.loopers.metrics.domain.EventSubscription
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductMetricsService(
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricsRepository: ProductMetricsRepository,
) {
    @Transactional
    fun handle(event: ProductEvent) = handleOnce(event.eventId) {
        when (event) {
            is ProductEvent.Liked -> productMetricsRepository.accumulate(event.productId, likeChange = 1)
            is ProductEvent.Unliked -> productMetricsRepository.accumulate(event.productId, likeChange = -1)
        }
    }

    @Transactional
    fun handle(event: OrderCreatedEvent) = handleOnce(event.eventId) {
        event.items.forEach { line ->
            productMetricsRepository.accumulate(line.productId, salesChange = line.quantity)
        }
    }

    @Transactional
    fun handle(event: ProductViewedEvent) = handleOnce(event.eventId) {
        productMetricsRepository.accumulate(event.productId, viewChange = 1)
    }

    private inline fun handleOnce(eventId: String, aggregate: () -> Unit) {
        if (eventHandledRepository.exists(eventId, EventSubscription.METRICS)) {
            return
        }
        aggregate()
        eventHandledRepository.markHandled(eventId, EventSubscription.METRICS)
    }
}
