package com.loopers.support.outbox.relay

internal data class LikeCountChangedPayload(
    val productId: Long?,
    val userId: Long?,
    val delta: Int?,
)
