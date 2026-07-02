package com.loopers.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.domain.order.event.OrderEvent
import com.loopers.domain.payment.event.PaymentEvent
import com.loopers.event.CatalogEventMessage
import com.loopers.event.OrderEventMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EventRecordService(
    private val eventOutboxRepository: EventOutboxRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${commerce.events.catalog-topic:catalog-events}")
    private val catalogTopic: String,
    @Value("\${commerce.events.order-topic:order-events}")
    private val orderTopic: String,
) {
    fun record(event: ProductLikeEvent.Like): EventOutbox {
        return saveCatalogMessage(ProductLikeExternalEventMessagePayload.from(event))
    }

    fun record(event: ProductLikeEvent.Unlike): EventOutbox {
        return saveCatalogMessage(ProductLikeExternalEventMessagePayload.from(event))
    }

    fun record(event: OrderEvent.Created): EventOutbox {
        return saveOrderMessage(OrderExternalEventMessagePayload.from(event))
    }

    fun record(event: PaymentEvent.Requested): EventOutbox {
        return saveOrderMessage(OrderExternalEventMessagePayload.from(event))
    }

    fun record(event: PaymentEvent.Succeeded): EventOutbox {
        return saveOrderMessage(OrderExternalEventMessagePayload.from(event))
    }

    fun record(event: PaymentEvent.Failed): EventOutbox {
        return saveOrderMessage(OrderExternalEventMessagePayload.from(event))
    }

    private fun saveCatalogMessage(message: CatalogEventMessage): EventOutbox {
        return eventOutboxRepository.save(
            EventOutbox(
                eventId = message.eventId,
                topic = catalogTopic,
                partitionKey = message.productId.toString(),
                eventType = message.eventType.name,
                payload = objectMapper.writeValueAsString(message),
            ),
        )
    }

    private fun saveOrderMessage(message: OrderEventMessage): EventOutbox {
        return eventOutboxRepository.save(
            EventOutbox(
                eventId = message.eventId,
                topic = orderTopic,
                partitionKey = message.orderId.toString(),
                eventType = message.eventType.name,
                payload = objectMapper.writeValueAsString(message),
            ),
        )
    }
}
