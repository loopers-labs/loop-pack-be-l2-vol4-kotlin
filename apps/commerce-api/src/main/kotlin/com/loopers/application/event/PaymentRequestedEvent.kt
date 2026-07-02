package com.loopers.application.event

import java.time.ZonedDateTime

data class PaymentRequestedEvent(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val callbackUrl: String,
    override val occurredAt: String = ZonedDateTime.now().toString(),
) : IntegrationEvent {
    override val eventType: String = EVENT_TYPE
    override val aggregateType: String = EventAggregateType.PAYMENT.value
    override val aggregateId: String = paymentId.toString()
    override val topic: String = EventTopic.PAYMENT_EVENTS.value

    companion object {
        const val EVENT_TYPE = "PAYMENT_REQUESTED"
    }
}
