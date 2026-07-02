package com.loopers.projection.like.port

import java.util.UUID

interface ProcessedKafkaEventRepository {
    fun recordIfAbsent(
        eventId: UUID,
        consumerGroup: String,
        eventType: String,
    ): Boolean
}
