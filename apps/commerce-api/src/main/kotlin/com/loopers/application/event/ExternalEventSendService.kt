package com.loopers.application.event

import com.loopers.domain.event.ExternalEventPublisher
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CouponIssueRequestMessage
import com.loopers.event.OrderEventMessage
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ExternalEventSendService(
    private val publisher: ExternalEventPublisher,
    private val eventOutboxRepository: EventOutboxRepository,
    @Value("\${commerce.events.catalog-topic:catalog-events}")
    private val catalogTopic: String,
    @Value("\${commerce.events.order-topic:order-events}")
    private val orderTopic: String,
    @Value("\${commerce.events.coupon-issue-request-topic:coupon-issue-requests}")
    private val couponIssueRequestTopic: String,
) {
    @Transactional
    fun send(message: CatalogEventMessage) {
        send(
            topic = catalogTopic,
            partitionKey = message.productId.toString(),
            eventId = message.eventId,
            message = message,
        )
    }

    @Transactional
    fun send(message: OrderEventMessage) {
        send(
            topic = orderTopic,
            partitionKey = message.orderId.toString(),
            eventId = message.eventId,
            message = message,
        )
    }

    @Transactional
    fun send(message: CouponIssueRequestMessage) {
        send(
            topic = couponIssueRequestTopic,
            partitionKey = message.couponId.toString(),
            eventId = message.eventId,
            message = message,
        )
    }

    private fun send(
        topic: String,
        partitionKey: String,
        eventId: String,
        message: Any,
    ) {
        publisher.publish(
            topic = topic,
            partitionKey = partitionKey,
            message = message,
        )

        val eventOutbox = eventOutboxRepository.findByEventId(eventId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Event outbox not found.")

        eventOutboxRepository.save(eventOutbox.published())
    }
}
