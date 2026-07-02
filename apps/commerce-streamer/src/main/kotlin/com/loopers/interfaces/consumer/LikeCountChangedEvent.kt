package com.loopers.interfaces.consumer

import com.fasterxml.jackson.annotation.JsonAlias
import java.util.UUID

data class LikeCountChangedEvent(
    val eventId: UUID,
    @JsonAlias("type")
    val eventType: String,
    val productId: Long,
    val userId: Long,
    val delta: Int,
)
