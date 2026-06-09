package com.loopers.domain.event

/**
 * 사용자가 상품의 좋아요를 취소했다는 사실. 발행: Like, 구독: Like(이력 append) / Product(like_count 감소).
 */
data class ProductUnlikedEvent(
    val userId: Long,
    val productId: Long,
)
