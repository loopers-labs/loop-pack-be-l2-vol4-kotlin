package com.loopers.application.event

enum class EventTopic(
    val value: String,
) {
    CATALOG_EVENTS("catalog-events"),
    ORDER_EVENTS("order-events"),
    PAYMENT_EVENTS("payment-events"),
    COUPON_ISSUE_REQUESTS("coupon-issue-requests"),
    ;

    companion object {
        const val PAYMENT_EVENTS_VALUE = "payment-events"
        const val COUPON_ISSUE_REQUESTS_VALUE = "coupon-issue-requests"
    }
}
