package com.loopers.product.domain.event

// 상품 상세 조회 사실 — 유실 허용이라 OutboxPublishable 을 구현하지 않는다(메모리 이벤트만).
// userId 는 product 조회 REST 노출 시 채운다 (현재 컨트롤러 부재로 행위자를 모름).
data class ProductViewedEvent(
    val productId: Long,
    val userId: Long? = null,
)
