package com.loopers.application.like

import com.loopers.application.event.UserActivityEvent

data class LikeChangedEvent(
    override val userId: Long,
    val productId: Long,
    val activated: Boolean,
) : UserActivityEvent {
    override val activityType: String = if (activated) "LIKE" else "UNLIKE"
    override val description: String = "좋아요 ${if (activated) "추가" else "취소"}: productId=$productId"
}
