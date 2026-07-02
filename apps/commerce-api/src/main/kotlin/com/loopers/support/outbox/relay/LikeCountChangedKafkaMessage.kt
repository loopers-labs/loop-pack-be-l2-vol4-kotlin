package com.loopers.support.outbox.relay

import java.util.UUID

data class LikeCountChangedKafkaMessage(
    val eventId: UUID,
    val eventType: String,
    val productId: Long,
    val userId: Long,
    val delta: Int,
)
