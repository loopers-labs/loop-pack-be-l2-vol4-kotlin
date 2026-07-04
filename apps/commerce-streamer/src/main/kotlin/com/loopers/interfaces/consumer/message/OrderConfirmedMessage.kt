package com.loopers.interfaces.consumer.message

import java.time.ZonedDateTime

data class OrderConfirmedMessage(
    val eventId: String,
    val orderId: Long,
    val items: List<Item>,
    val occurredAt: ZonedDateTime,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}
