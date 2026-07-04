package com.loopers.interfaces.consumer.message

import java.time.ZonedDateTime

data class ProductViewedMessage(
    val eventId: String,
    val productId: Long,
    val occurredAt: ZonedDateTime,
)
