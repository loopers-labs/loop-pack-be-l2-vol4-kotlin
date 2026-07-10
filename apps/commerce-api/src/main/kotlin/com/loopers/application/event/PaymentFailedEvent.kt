package com.loopers.application.event

import java.time.ZonedDateTime

data class PaymentFailedEvent(
    override val userId: Long,
    val orderId: Long,
    val transactionKey: String,
    val reason: String?,
    override val occurredAt: String = ZonedDateTime.now().toString(),
) : UserActivityEvent, IntegrationEvent {
    override val activityType: String = EVENT_TYPE
    override val eventType: String = EVENT_TYPE
    override val description: String = "결제 실패: orderId=$orderId, transactionKey=$transactionKey, reason=$reason"
    override val aggregateType: String = EventAggregateType.ORDER.value
    override val aggregateId: String = orderId.toString()
    override val topic: String = EventTopic.ORDER_EVENTS.value

    companion object {
        const val EVENT_TYPE = "PAYMENT_FAILED"
    }
}
