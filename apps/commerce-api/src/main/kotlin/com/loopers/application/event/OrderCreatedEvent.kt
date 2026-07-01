package com.loopers.application.event

data class OrderCreatedEvent(
    override val userId: Long,
    val orderId: Long,
    val totalAmount: Long,
) : UserActivityEvent {
    override val activityType: String = "ORDER_CREATED"
    override val description: String = "주문 생성: orderId=$orderId, totalAmount=$totalAmount"
}
