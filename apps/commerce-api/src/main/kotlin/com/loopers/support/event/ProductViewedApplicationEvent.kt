package com.loopers.support.event

import java.time.ZonedDateTime

data class ProductViewedApplicationEvent(
    val productId: Long,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
