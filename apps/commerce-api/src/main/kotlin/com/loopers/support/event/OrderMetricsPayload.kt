package com.loopers.support.event

internal data class OrderMetricsPayload(
    val orderId: Long,
    val items: List<CommerceEventOrderItem>,
)
