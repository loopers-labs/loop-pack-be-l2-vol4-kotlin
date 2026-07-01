package com.loopers.application.event

enum class EventTopic(
    val value: String,
) {
    CATALOG_EVENTS("catalog-events"),
    ORDER_EVENTS("order-events"),
    COUPON_ISSUE_REQUESTS("coupon-issue-requests"),
    ;

    companion object {
        const val COUPON_ISSUE_REQUESTS_VALUE = "coupon-issue-requests"
    }
}
