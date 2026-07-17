package com.loopers.support.event

import java.time.ZonedDateTime

data class PaymentApprovedApplicationEvent(
    val paymentId: Long,
    val orderId: Long,
    val items: List<CommerceEventOrderItem>,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
