package com.loopers.domain.order.event

import java.time.ZonedDateTime
import java.util.UUID

object OrderEvent {
    data class Created(
        val orderId: Long,
        val orderNumber: String,
        val memberId: Long,
        val amount: Long,
        val eventId: String = UUID.randomUUID().toString(),
        val occurredAt: ZonedDateTime = ZonedDateTime.now(),
    )
}
