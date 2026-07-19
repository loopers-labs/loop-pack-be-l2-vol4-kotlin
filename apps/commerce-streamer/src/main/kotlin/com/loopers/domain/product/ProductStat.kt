package com.loopers.domain.product

class ProductStat(
    val id: Long = 0L,
    val productId: Long,
    val brandId: Long,
    var likeCount: Long,
    var salesCount: Long,
    var viewCount: Long,
    var latestEventVersion: Long,
)
