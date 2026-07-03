package com.loopers.projection.like.application

import java.time.ZonedDateTime
import java.util.UUID

data class ProductMetricsProjectionCommand(
    val eventId: UUID,
    val consumerGroup: String,
    val eventType: String,
    val occurredAt: ZonedDateTime,
    val deltas: List<ProductMetricsDelta>,
)
