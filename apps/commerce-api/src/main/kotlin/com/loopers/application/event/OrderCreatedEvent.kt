package com.loopers.application.event

import java.time.ZonedDateTime

data class OrderCreatedEvent(
    override val userId: Long,
    val orderId: Long,
    val totalAmount: Long,
    val items: List<OrderItemPayload>,
    override val occurredAt: String = ZonedDateTime.now().toString(),
) : UserActivityEvent, IntegrationEvent {
    override val activityType: String = EVENT_TYPE
    override val eventType: String = EVENT_TYPE
    override val description: String = "주문 생성: orderId=$orderId, totalAmount=$totalAmount"
    override val aggregateType: String = EventAggregateType.ORDER.value
    override val aggregateId: String = orderId.toString()
    override val topic: String = EventTopic.ORDER_EVENTS.value

    companion object {
        const val EVENT_TYPE = "ORDER_CREATED"
    }
}

data class OrderItemPayload(
    val productId: Long,
    val quantity: Int,
    val amount: Long,
)
