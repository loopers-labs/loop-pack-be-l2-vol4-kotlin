package com.loopers.application.like

data class LikeChangedEvent(
    val userId: Long,
    val productId: Long,
    val activated: Boolean,
)
