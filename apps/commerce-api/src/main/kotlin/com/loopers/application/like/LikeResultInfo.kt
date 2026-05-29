package com.loopers.application.like

data class LikeResultInfo(
    val userId: Long,
    val productId: Long,
    val changed: Boolean,
)
