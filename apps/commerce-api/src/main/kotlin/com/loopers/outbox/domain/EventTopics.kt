package com.loopers.outbox.domain

object EventTopics {
    const val PRODUCT_EVENTS = "product-events"
    const val ORDER_EVENTS = "order-events"
    const val USER_ACTION_EVENTS = "user-action-events"

    fun forAggregateType(aggregateType: String): String = when (aggregateType) {
        "ORDER" -> ORDER_EVENTS
        "PRODUCT" -> PRODUCT_EVENTS
        else -> error("outbox 토픽 매핑이 없는 aggregateType: $aggregateType")
    }
}
