package com.loopers.application.like

data class LikedProductSummary(
    val productId: Long,
    val name: String,
    val price: Long,
    val brandName: String,
)
