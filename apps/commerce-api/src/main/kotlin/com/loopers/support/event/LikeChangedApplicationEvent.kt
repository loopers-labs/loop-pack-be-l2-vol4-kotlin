package com.loopers.support.event

import java.time.ZonedDateTime

data class LikeChangedApplicationEvent(
    val userId: Long,
    val productId: Long,
    val delta: Int,
    val occurredAt: ZonedDateTime = ZonedDateTime.now(),
)
