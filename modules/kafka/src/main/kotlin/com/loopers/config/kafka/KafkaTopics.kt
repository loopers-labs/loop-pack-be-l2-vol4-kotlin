package com.loopers.config.kafka

object KafkaTopics {
    const val CATALOG_EVENTS = "catalog-events"
    const val ORDER_EVENTS = "order-events"
    const val VIEW_EVENTS = "view-events"
    const val COUPON_ISSUE_REQUESTS = "coupon-issue-requests"
    const val COUPON_ISSUE_REQUESTS_DLT = "$COUPON_ISSUE_REQUESTS-dlt"
    const val USER_ACTION_EVENTS = "user-action-events"
    const val USER_ACTION_EVENTS_DLT = "$USER_ACTION_EVENTS-dlt"
}
