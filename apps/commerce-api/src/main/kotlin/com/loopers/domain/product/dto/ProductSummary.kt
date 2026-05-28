package com.loopers.domain.product.dto

data class ProductSummary(
    val productId: Long,
    val productName: String,
    val price: Long,
    val imageUrl: String,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
)
