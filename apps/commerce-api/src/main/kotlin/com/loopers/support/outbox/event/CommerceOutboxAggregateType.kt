package com.loopers.support.outbox.event

enum class CommerceOutboxAggregateType(
    val value: String,
) {
    PRODUCT("PRODUCT"),
    ORDER("ORDER"),
    PAYMENT("PAYMENT"),
    COUPON_ISSUE_REQUEST("COUPON_ISSUE_REQUEST"),
}
