package com.loopers.infrastructure.order

import com.loopers.domain.order.event.OrderEvent
import com.loopers.domain.order.event.OrderEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class OrderCoreEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : OrderEventPublisher {
    override fun publish(event: OrderEvent.Created) {
        applicationEventPublisher.publishEvent(event)
    }
}
