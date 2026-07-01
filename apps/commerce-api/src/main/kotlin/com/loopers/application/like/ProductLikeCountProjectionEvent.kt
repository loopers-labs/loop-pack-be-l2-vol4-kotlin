package com.loopers.application.like

import com.loopers.application.event.UserActivityEvent

data class ProductLikeCountProjectionIncreasedEvent(
    override val userId: Long,
    val productId: Long,
) : UserActivityEvent {
    override val activityType: String = "LIKE"
    override val description: String = "좋아요 추가: productId=$productId"
}

data class ProductLikeCountProjectionDecreasedEvent(
    override val userId: Long,
    val productId: Long,
) : UserActivityEvent {
    override val activityType: String = "UNLIKE"
    override val description: String = "좋아요 취소: productId=$productId"
}
