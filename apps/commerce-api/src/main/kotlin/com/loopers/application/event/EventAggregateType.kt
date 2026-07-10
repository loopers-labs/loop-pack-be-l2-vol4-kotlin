package com.loopers.application.event

enum class EventAggregateType(
    val value: String,
) {
    PRODUCT("product"),
    ORDER("order"),
    PAYMENT("payment"),
    COUPON("coupon"),
}
