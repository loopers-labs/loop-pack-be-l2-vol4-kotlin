package com.loopers.domain.order

interface DataPlatformClient {
    fun send(event: OrderCreatedEvent)
}
