package com.loopers.domain.product.event

interface ProductEventPublisher {
    fun publish(event: ProductEvent.Viewed)
}
