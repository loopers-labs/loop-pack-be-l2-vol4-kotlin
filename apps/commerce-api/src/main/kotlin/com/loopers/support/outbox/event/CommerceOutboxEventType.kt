package com.loopers.support.outbox.event

enum class CommerceOutboxEventType(
    val topicName: String,
) {
    LIKE_COUNT_CHANGED_V1("catalog-events"),
    PRODUCT_VIEWED_V1("catalog-events"),
    ORDER_CREATED_V1("order-events"),
    ORDER_PAID_V1("order-events"),
    ORDER_FAILED_V1("order-events"),
    PAYMENT_APPROVED("order-events"),
    PAYMENT_FAILED("order-events"),
    COUPON_ISSUE_REQUESTED_V1("coupon-issue-requests"),
}
