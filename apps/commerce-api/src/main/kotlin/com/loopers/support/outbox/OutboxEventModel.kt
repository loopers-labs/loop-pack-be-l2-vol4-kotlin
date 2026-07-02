package com.loopers.support.outbox

import java.time.ZonedDateTime
import java.util.UUID

enum class OutboxEventStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED,
}

data class OutboxEventModel(
    val id: Long = 0L,
    val eventId: UUID = UUID.randomUUID(),
    val type: String,
    val aggregateType: String,
    val aggregateId: Long,
    val payload: String,
    val status: OutboxEventStatus = OutboxEventStatus.PENDING,
    val retryCount: Int = 0,
    val nextRetryAt: ZonedDateTime? = null,
    val lastError: String? = null,
    val publishedAt: ZonedDateTime? = null,
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
)
