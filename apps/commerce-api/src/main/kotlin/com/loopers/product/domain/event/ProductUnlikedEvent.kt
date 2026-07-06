package com.loopers.product.domain.event

import com.loopers.outbox.domain.OutboxPublishable
import java.util.UUID

/**
 * 사용자가 상품의 좋아요를 취소했다는 사실. 발행: Like, 구독: Like(이력 append) / Product(like_count 감소).
 * 시스템 경계 밖 집계(product_metrics)로도 나가므로 outbox 로 승격 — 메모리 구독은 그대로(이중 발행 아님).
 */
data class ProductUnlikedEvent(
    val userId: Long,
    val productId: Long,
    override val eventId: String = UUID.randomUUID().toString(),
) : OutboxPublishable {
    override val aggregateType: String get() = "PRODUCT"
    override val aggregateId: Long get() = productId
    override val eventType: String get() = "ProductUnlikedEvent"
}
