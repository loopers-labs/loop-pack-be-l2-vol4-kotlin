package com.loopers.domain.order.event

interface OrderEventPublisher {
    fun publish(event: OrderEvent.Created)
}
