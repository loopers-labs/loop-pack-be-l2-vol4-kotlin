package com.loopers.event

import java.time.ZonedDateTime

data class OrderEventMessage(
    val eventId: String,
    val eventType: OrderEventType,
    val aggregateId: Long,
    val orderId: Long,
    val orderNumber: String,
    val memberId: Long,
    val paymentId: Long?,
    val amount: Long,
    val occurredAt: ZonedDateTime,
)

enum class OrderEventType {
    ORDER_CREATED,
    PAYMENT_REQUESTED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
}
