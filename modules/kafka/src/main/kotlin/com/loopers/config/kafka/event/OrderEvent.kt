package com.loopers.config.kafka.event

import java.time.Instant

/**
 * 주문 도메인 이벤트 메시지.
 * order-events 토픽으로 발행되며, Consumer가 판매량 집계에 사용한다.
 *
 * @property eventId Outbox에서 부여하는 고유 ID (멱등 처리 키)
 * @property eventType 이벤트 종류 (ORDER_CREATED, ORDER_COMPLETED, ORDER_CANCELLED)
 * @property orderId 주문 ID (파티션 키로도 사용)
 * @property userId 주문자 ID
 * @property items 주문 상품 목록
 * @property totalPrice 총 결제 금액
 * @property reason 취소 사유 (ORDER_CANCELLED 시에만 존재)
 * @property occurredAt 이벤트 발생 시각
 * @property version 이벤트 스키마 버전
 */
data class OrderEvent(
    val eventId: String,
    val eventType: String,
    val orderId: Long,
    val userId: Long,
    val items: List<OrderEventItem> = emptyList(),
    val totalPrice: Long = 0,
    val reason: String? = null,
    val occurredAt: Instant = Instant.now(),
    val version: Long = 1,
)

/**
 * 주문 이벤트에 포함되는 개별 상품 정보.
 */
data class OrderEventItem(
    val productId: Long,
    val productName: String,
    val quantity: Long,
    val price: Long,
)
