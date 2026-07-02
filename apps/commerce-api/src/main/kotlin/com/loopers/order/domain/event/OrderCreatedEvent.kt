package com.loopers.order.domain.event

import com.loopers.outbox.domain.OutboxPublishable
import java.util.UUID

// 주문 생성 사실 — 시스템 경계 밖(판매량 집계·전파)으로 나가므로 outbox 로 승격된다.
data class OrderCreatedEvent(
    val orderId: Long,
    val orderKey: String,
    val userId: Long,
    val totalAmount: Long,
    val items: List<OrderCreatedItem>,
    override val eventId: String = UUID.randomUUID().toString(),
) : OutboxPublishable {
    override val aggregateType: String get() = "ORDER"
    override val aggregateId: Long get() = orderId
    override val eventType: String get() = "OrderCreatedEvent"
}

data class OrderCreatedItem(
    val productId: Long,
    val quantity: Int,
    val unitPrice: Long,
)
