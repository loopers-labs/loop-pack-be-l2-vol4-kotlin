package com.loopers.application.like

data class ProductLikeResult(
    val productId: Long,
    val liked: Boolean,
    val changed: Boolean,
)
