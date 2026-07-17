package com.loopers.support.event

import java.time.ZonedDateTime

internal data class LikeChangedPayload(
    val productId: Long,
    val userId: Long,
    val delta: Int,
    val occurredAt: ZonedDateTime,
)
