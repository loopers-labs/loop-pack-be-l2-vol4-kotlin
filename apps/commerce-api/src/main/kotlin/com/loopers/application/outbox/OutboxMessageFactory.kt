package com.loopers.application.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.like.LikeDeletedEvent
import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.payment.PaymentSucceededEvent
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

data class OutboxDraft(val eventId: String, val topic: String, val partitionKey: String, val payload: String)

@Component
class OutboxMessageFactory(
    private val objectMapper: ObjectMapper,
) {
    fun from(event: Any): OutboxDraft? =
        when (event) {
            is LikeCreatedEvent -> catalog("LIKE_ADDED", event.productId)
            is LikeDeletedEvent -> catalog("LIKE_REMOVED", event.productId)
            is PaymentSucceededEvent -> order(event)
            else -> null
        }

    private fun catalog(type: String, productId: Long): OutboxDraft {
        val eventId = UUID.randomUUID().toString()
        val payload = objectMapper.writeValueAsString(
            linkedMapOf(
                "eventId" to eventId,
                "type" to type,
                "productId" to productId,
                "occurredAt" to ZonedDateTime.now().toString(),
            ),
        )
        return OutboxDraft(eventId, KafkaTopics.CATALOG_EVENTS, productId.toString(), payload)
    }

    private fun order(event: PaymentSucceededEvent): OutboxDraft {
        val eventId = UUID.randomUUID().toString()
        val payload = objectMapper.writeValueAsString(
            linkedMapOf(
                "eventId" to eventId,
                "type" to "PAYMENT_SUCCEEDED",
                "orderId" to event.orderId,
                "userId" to event.userId,
                "items" to event.items.map { linkedMapOf("productId" to it.productId, "quantity" to it.quantity) },
                "occurredAt" to ZonedDateTime.now().toString(),
            ),
        )
        return OutboxDraft(eventId, KafkaTopics.ORDER_EVENTS, event.orderId.toString(), payload)
    }
}
