package com.loopers.application.event

interface IntegrationEvent {
    val eventType: String
    val occurredAt: String
    val aggregateType: String
    val aggregateId: String
    val topic: String
    val partitionKey: String
        get() = aggregateId
}
