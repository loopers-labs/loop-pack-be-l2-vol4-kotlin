package com.loopers.application.event

import com.loopers.domain.order.event.OrderEvent
import com.loopers.domain.payment.event.PaymentEvent
import com.loopers.event.OrderEventItemMessage
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import java.time.ZonedDateTime

object OrderExternalEventMessagePayload {
    fun from(event: OrderEvent.Created): OrderEventMessage {
        return OrderEventMessage(
            eventId = event.eventId,
            eventType = OrderEventType.ORDER_CREATED,
            aggregateId = event.orderId,
            orderId = event.orderId,
            orderNumber = event.orderNumber,
            memberId = event.memberId,
            paymentId = null,
            amount = event.amount,
            occurredAt = event.occurredAt,
        )
    }

    fun from(event: PaymentEvent.Requested): OrderEventMessage {
        return paymentMessage(
            eventId = event.eventId,
            eventType = OrderEventType.PAYMENT_REQUESTED,
            paymentId = event.paymentId,
            orderId = event.orderId,
            orderNumber = event.orderNumber,
            memberId = event.memberId,
            amount = event.amount,
            occurredAt = event.occurredAt,
        )
    }

    fun from(event: PaymentEvent.Succeeded): OrderEventMessage {
        return paymentMessage(
            eventId = event.eventId,
            eventType = OrderEventType.PAYMENT_SUCCEEDED,
            paymentId = event.paymentId,
            orderId = event.orderId,
            orderNumber = event.orderNumber,
            memberId = event.memberId,
            amount = event.amount,
            items = event.items.map { item ->
                OrderEventItemMessage(
                    productId = item.productId,
                    quantity = item.quantity,
                )
            },
            occurredAt = event.occurredAt,
        )
    }

    fun from(event: PaymentEvent.Failed): OrderEventMessage {
        return paymentMessage(
            eventId = event.eventId,
            eventType = OrderEventType.PAYMENT_FAILED,
            paymentId = event.paymentId,
            orderId = event.orderId,
            orderNumber = event.orderNumber,
            memberId = event.memberId,
            amount = event.amount,
            occurredAt = event.occurredAt,
        )
    }

    private fun paymentMessage(
        eventId: String,
        eventType: OrderEventType,
        paymentId: Long,
        orderId: Long,
        orderNumber: String,
        memberId: Long,
        amount: Long,
        items: List<OrderEventItemMessage> = emptyList(),
        occurredAt: ZonedDateTime,
    ): OrderEventMessage {
        return OrderEventMessage(
            eventId = eventId,
            eventType = eventType,
            aggregateId = orderId,
            orderId = orderId,
            orderNumber = orderNumber,
            memberId = memberId,
            paymentId = paymentId,
            amount = amount,
            items = items,
            occurredAt = occurredAt,
        )
    }
}
