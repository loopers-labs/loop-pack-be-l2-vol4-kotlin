package com.loopers.application.event

import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeExternalEventPublisher
import com.loopers.event.CatalogEventMessage
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductLikeExternalEventSendService(
    private val publisher: ProductLikeExternalEventPublisher,
    private val eventOutboxRepository: EventOutboxRepository,
    @Value("\${commerce.events.catalog-topic:catalog-events}")
    private val catalogTopic: String,
) {
    @Transactional
    fun send(message: CatalogEventMessage) {
        publisher.publish(
            topic = catalogTopic,
            partitionKey = message.productId.toString(),
            message = message,
        )

        val eventOutbox = eventOutboxRepository.findByEventId(message.eventId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Event outbox not found.")

        eventOutboxRepository.save(eventOutbox.published())
    }
}
