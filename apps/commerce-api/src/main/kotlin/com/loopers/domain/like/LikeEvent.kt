package com.loopers.domain.like

import java.time.ZonedDateTime

class LikeEvent {
    data class Changed(
        val eventId: String,
        val userId: Long,
        val productId: Long,
        val type: LikeChangeType,
        val occurredAt: ZonedDateTime,
    )

    enum class LikeChangeType { LIKED, UNLIKED }
}
