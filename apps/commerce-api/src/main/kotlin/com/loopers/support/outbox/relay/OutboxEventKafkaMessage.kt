package com.loopers.support.outbox.relay

import java.util.UUID

data class OutboxEventKafkaMessage(
    val eventId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: Long,
    val payload: String,
    val createdAt: String,
)
