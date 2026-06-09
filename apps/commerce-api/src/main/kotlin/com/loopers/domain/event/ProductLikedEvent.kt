package com.loopers.domain.event

/**
 * 사용자가 상품에 좋아요를 등록했다는 사실. 발행: Like, 구독: Like(이력 append) / Product(like_count 증가).
 * 모듈 분리 시 이 event 패키지만 양쪽이 의존한다(도메인 간 직접 의존 없음).
 */
data class ProductLikedEvent(
    val userId: Long,
    val productId: Long,
)
