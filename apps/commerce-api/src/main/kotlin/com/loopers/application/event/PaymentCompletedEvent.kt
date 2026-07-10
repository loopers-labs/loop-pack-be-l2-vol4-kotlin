package com.loopers.application.event

import java.time.ZonedDateTime

data class PaymentCompletedEvent(
    override val userId: Long,
    val orderId: Long,
    val transactionKey: String,
    val amount: Long,
    val items: List<OrderItemPayload>,
    override val occurredAt: String = ZonedDateTime.now().toString(),
) : UserActivityEvent, IntegrationEvent {
    override val activityType: String = EVENT_TYPE
    override val eventType: String = EVENT_TYPE
    override val description: String = "결제 성공: orderId=$orderId, transactionKey=$transactionKey, amount=$amount"
    override val aggregateType: String = EventAggregateType.ORDER.value
    override val aggregateId: String = orderId.toString()
    override val topic: String = EventTopic.ORDER_EVENTS.value

    companion object {
        const val EVENT_TYPE = "PAYMENT_SUCCESS"
    }
}
