package com.loopers.support.outbox.event

enum class CommerceOutboxEventType(
    val defaultTopicName: String?,
) {
    LIKE_COUNT_CHANGED_V1("catalog-events"),
    PRODUCT_VIEWED_V1("catalog-events"),
    ORDER_CREATED_V1("order-events"),
    ORDER_PAID_V1("order-events"),
    ORDER_FAILED_V1("order-events"),
    COUPON_ISSUE_REQUESTED_V1(null),
}
