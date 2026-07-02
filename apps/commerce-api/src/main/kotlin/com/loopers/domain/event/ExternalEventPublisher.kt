package com.loopers.domain.event

interface ExternalEventPublisher {
    fun publish(topic: String, partitionKey: String, message: Any)
}
