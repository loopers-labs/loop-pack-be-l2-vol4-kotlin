package com.loopers.support.event

import java.time.ZonedDateTime

data class OrderCreatedApplicationEvent(
    val orderId: Long,
    val items: List<CommerceEventOrderItem>,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
