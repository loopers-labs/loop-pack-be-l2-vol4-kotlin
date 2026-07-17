package com.loopers.config.kafka

/**
 * Kafka 토픽 상수 정의.
 * 토픽 이름은 도메인 단위로 분리하며, PartitionKey 기준은 아래와 같다.
 * - catalog-events: productId
 * - order-events: orderId
 * - coupon-issue-requests: couponId
 */
object Topics {
    /** 상품 관련 이벤트 (조회, 좋아요 등) */
    const val CATALOG_EVENTS = "catalog-events"

    /** 주문 관련 이벤트 (생성, 완료, 취소) */
    const val ORDER_EVENTS = "order-events"

    /** 선착순 쿠폰 발급 요청 */
    const val COUPON_ISSUE_REQUESTS = "coupon-issue-requests"
}
