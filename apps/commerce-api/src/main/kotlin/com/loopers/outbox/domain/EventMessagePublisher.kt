package com.loopers.outbox.domain

interface EventMessagePublisher {
    fun publish(topic: String, partitionKey: String, message: Any)
}
