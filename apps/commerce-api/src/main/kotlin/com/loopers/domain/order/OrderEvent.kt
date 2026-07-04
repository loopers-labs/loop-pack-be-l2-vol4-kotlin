package com.loopers.domain.order

import java.time.ZonedDateTime

object OrderEvent {
    data class Confirmed(
        val eventId: String,
        val orderId: Long,
        val userId: Long,
        val items: List<Item>,
        val occurredAt: ZonedDateTime,
    )

    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
