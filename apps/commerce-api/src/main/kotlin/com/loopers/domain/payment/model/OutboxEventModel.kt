package com.loopers.domain.payment.model

import java.time.ZonedDateTime

enum class OutboxEventType {
    PAYMENT_STATUS_SYNC_REQUESTED,
    PAYMENT_APPROVED,
    PAYMENT_FAILED,
}

enum class OutboxEventStatus {
    PENDING,
    PROCESSED,
    FAILED,
}

data class OutboxEventModel(
    val id: Long = 0L,
    val type: OutboxEventType,
    val aggregateId: Long,
    val payload: String,
    val status: OutboxEventStatus = OutboxEventStatus.PENDING,
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
)
