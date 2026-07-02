package com.loopers.domain.product

class ProductStatProjection(
    val id: Long = 0L,
    val productId: Long,
    val brandId: Long,
    var likeCount: Long,
    var salesCount: Long,
    var latestEventVersion: Long,
)
