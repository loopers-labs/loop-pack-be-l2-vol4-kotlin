package com.loopers.application.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.event.ExternalEventPublisher
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.model.EventOutboxStatus
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.CouponIssueRequestMessage
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.ZonedDateTime

class EventOutboxRelayServiceTest {
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    @DisplayName("미발행 outbox record 를 Kafka 로 발행하고 발행 완료로 변경한다")
    @Test
    fun publishesPendingOutboxAndMarksPublished() {
        val message = createMessage("event-1")
        val repository = FakeEventOutboxRepository(createOutbox(message))
        val publisher = RecordingExternalEventPublisher()
        val service = EventOutboxRelayService(
            eventOutboxRepository = repository,
            publisher = publisher,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )

        val relayedCount = service.relayPending(limit = 100)

        val outbox = repository.findByEventId("event-1")
        val publishedMessage = publisher.publishedMessages.single()
        val publishedPayload = publishedMessage.message as CatalogEventMessage
        assertAll(
            { assertThat(relayedCount).isEqualTo(1) },
            { assertThat(publishedMessage.topic).isEqualTo("catalog-events") },
            { assertThat(publishedMessage.partitionKey).isEqualTo("10") },
            { assertThat(publishedPayload.eventId).isEqualTo(message.eventId) },
            { assertThat(publishedPayload.eventType).isEqualTo(message.eventType) },
            { assertThat(publishedPayload.productId).isEqualTo(message.productId) },
            { assertThat(publishedPayload.occurredAt.toInstant()).isEqualTo(message.occurredAt.toInstant()) },
            { assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PUBLISHED) },
            { assertThat(outbox?.publishedAt).isNotNull() },
        )
    }

    @DisplayName("미발행 order outbox record 를 Kafka 로 발행하고 발행 완료로 변경한다")
    @Test
    fun publishesPendingOrderOutboxAndMarksPublished() {
        val message = createOrderMessage("event-2")
        val repository = FakeEventOutboxRepository(createOrderOutbox(message))
        val publisher = RecordingExternalEventPublisher()
        val service = EventOutboxRelayService(
            eventOutboxRepository = repository,
            publisher = publisher,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )

        val relayedCount = service.relayPending(limit = 100)

        val outbox = repository.findByEventId("event-2")
        val publishedMessage = publisher.publishedMessages.single()
        val publishedPayload = publishedMessage.message as OrderEventMessage
        assertAll(
            { assertThat(relayedCount).isEqualTo(1) },
            { assertThat(publishedMessage.topic).isEqualTo("order-events") },
            { assertThat(publishedMessage.partitionKey).isEqualTo("20") },
            { assertThat(publishedPayload.eventId).isEqualTo(message.eventId) },
            { assertThat(publishedPayload.eventType).isEqualTo(message.eventType) },
            { assertThat(publishedPayload.orderId).isEqualTo(message.orderId) },
            { assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PUBLISHED) },
            { assertThat(outbox?.publishedAt).isNotNull() },
        )
    }

    @DisplayName("미발행 coupon outbox record 를 Kafka 로 발행하고 발행 완료로 변경한다")
    @Test
    fun publishesPendingCouponIssueRequestOutboxAndMarksPublished() {
        val message = createCouponIssueRequestMessage("event-3")
        val repository = FakeEventOutboxRepository(createCouponIssueRequestOutbox(message))
        val publisher = RecordingExternalEventPublisher()
        val service = EventOutboxRelayService(
            eventOutboxRepository = repository,
            publisher = publisher,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )

        val relayedCount = service.relayPending(limit = 100)

        val outbox = repository.findByEventId("event-3")
        val publishedMessage = publisher.publishedMessages.single()
        val publishedPayload = publishedMessage.message as CouponIssueRequestMessage
        assertAll(
            { assertThat(relayedCount).isEqualTo(1) },
            { assertThat(publishedMessage.topic).isEqualTo("coupon-issue-requests") },
            { assertThat(publishedMessage.partitionKey).isEqualTo("30") },
            { assertThat(publishedPayload.eventId).isEqualTo(message.eventId) },
            { assertThat(publishedPayload.requestId).isEqualTo(message.requestId) },
            { assertThat(publishedPayload.couponId).isEqualTo(message.couponId) },
            { assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PUBLISHED) },
            { assertThat(outbox?.publishedAt).isNotNull() },
        )
    }

    @DisplayName("Kafka 발행에 실패하면 미발행 상태로 두고 재시도 횟수를 증가시킨다")
    @Test
    fun keepsPendingAndIncrementsRetryCount_whenPublishFails() {
        val message = createMessage("event-1")
        val repository = FakeEventOutboxRepository(createOutbox(message))
        val service = EventOutboxRelayService(
            eventOutboxRepository = repository,
            publisher = FailingExternalEventPublisher(),
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )

        val relayedCount = service.relayPending(limit = 100)

        val outbox = repository.findByEventId("event-1")
        assertAll(
            { assertThat(relayedCount).isEqualTo(1) },
            { assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PENDING) },
            { assertThat(outbox?.retryCount).isEqualTo(1) },
            { assertThat(outbox?.publishedAt).isNull() },
        )
    }

    private fun createOutbox(message: CatalogEventMessage): EventOutbox {
        return EventOutbox(
            id = 1L,
            eventId = message.eventId,
            topic = "catalog-events",
            partitionKey = message.productId.toString(),
            eventType = message.eventType.name,
            payload = objectMapper.writeValueAsString(message),
        )
    }

    private fun createOrderOutbox(message: OrderEventMessage): EventOutbox {
        return EventOutbox(
            id = 1L,
            eventId = message.eventId,
            topic = "order-events",
            partitionKey = message.orderId.toString(),
            eventType = message.eventType.name,
            payload = objectMapper.writeValueAsString(message),
        )
    }

    private fun createCouponIssueRequestOutbox(message: CouponIssueRequestMessage): EventOutbox {
        return EventOutbox(
            id = 1L,
            eventId = message.eventId,
            topic = "coupon-issue-requests",
            partitionKey = message.couponId.toString(),
            eventType = "COUPON_ISSUE_REQUESTED",
            payload = objectMapper.writeValueAsString(message),
        )
    }

    private fun createMessage(eventId: String): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = eventId,
            eventType = CatalogEventType.PRODUCT_LIKED,
            aggregateId = 10L,
            productId = 10L,
            brandId = 100L,
            memberId = 1L,
            version = 123L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private fun createOrderMessage(eventId: String): OrderEventMessage {
        return OrderEventMessage(
            eventId = eventId,
            eventType = OrderEventType.PAYMENT_SUCCEEDED,
            aggregateId = 20L,
            orderId = 20L,
            orderNumber = "order-20",
            memberId = 1L,
            paymentId = 30L,
            amount = 10_000L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private fun createCouponIssueRequestMessage(eventId: String): CouponIssueRequestMessage {
        return CouponIssueRequestMessage(
            eventId = eventId,
            requestId = "request-1",
            couponId = 30L,
            memberId = 1L,
            requestedAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private data class PublishedMessage(
        val topic: String,
        val partitionKey: String,
        val message: Any,
    )

    private class RecordingExternalEventPublisher : ExternalEventPublisher {
        val publishedMessages = mutableListOf<PublishedMessage>()

        override fun publish(topic: String, partitionKey: String, message: Any) {
            publishedMessages.add(PublishedMessage(topic, partitionKey, message))
        }
    }

    private class FailingExternalEventPublisher : ExternalEventPublisher {
        override fun publish(topic: String, partitionKey: String, message: Any) {
            throw IllegalStateException("kafka failed")
        }
    }

    private class FakeEventOutboxRepository(
        eventOutbox: EventOutbox,
    ) : EventOutboxRepository {
        private val records = linkedMapOf(eventOutbox.eventId to eventOutbox)

        override fun save(eventOutbox: EventOutbox): EventOutbox {
            records[eventOutbox.eventId] = eventOutbox
            return eventOutbox
        }

        override fun findByEventId(eventId: String): EventOutbox? {
            return records[eventId]
        }

        override fun findPending(limit: Int): List<EventOutbox> {
            return records.values
                .filter { it.status == EventOutboxStatus.PENDING }
                .take(limit)
        }
    }
}
