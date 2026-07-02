package com.loopers.projection.like.application

import java.util.UUID

data class LikeCountProjectionCommand(
    val eventId: UUID,
    val consumerGroup: String,
    val eventType: String,
    val productId: Long,
    val delta: Int,
)
