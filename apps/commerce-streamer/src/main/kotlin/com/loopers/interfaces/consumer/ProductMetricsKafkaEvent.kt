package com.loopers.interfaces.consumer

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.UUID

data class ProductMetricsKafkaEvent(
    val eventId: UUID,
    @JsonAlias("type")
    val eventType: String,
    val aggregateType: String? = null,
    val aggregateId: Long? = null,
    val payload: String? = null,
    val createdAt: String? = null,
    val productId: Long? = null,
    val userId: Long? = null,
    val delta: Int? = null,
)
