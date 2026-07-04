package com.loopers.interfaces.consumer.message

import java.time.ZonedDateTime

data class LikeChangedMessage(
    val eventId: String,
    val userId: Long,
    val productId: Long,
    val type: LikedType,
    val occurredAt: ZonedDateTime,
) {
    enum class LikedType {
        LIKED, UNLIKED
    }
}
