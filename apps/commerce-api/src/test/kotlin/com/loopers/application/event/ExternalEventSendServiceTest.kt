package com.loopers.application.event

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
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class ExternalEventSendServiceTest {
    @DisplayName("catalog Kafka 발행에 성공하면 outbox record 를 발행 완료로 변경한다")
    @Test
    fun marksCatalogOutboxPublished_whenKafkaSendSucceeds() {
        val message = createCatalogMessage()
        val publisher = RecordingExternalEventPublisher()
        val repository = FakeEventOutboxRepository(createCatalogOutbox(message))
        val service = createService(publisher = publisher, repository = repository)

        service.send(message)

        assertThat(publisher.publishedMessages).containsExactly(PublishedMessage("catalog-events", "10", message))
        val outbox = repository.findByEventId("catalog-event-1")
        assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PUBLISHED)
        assertThat(outbox?.publishedAt).isNotNull()
    }

    @DisplayName("order Kafka 발행에 성공하면 outbox record 를 발행 완료로 변경한다")
    @Test
    fun marksOrderOutboxPublished_whenKafkaSendSucceeds() {
        val message = createOrderMessage()
        val publisher = RecordingExternalEventPublisher()
        val repository = FakeEventOutboxRepository(createOrderOutbox(message))
        val service = createService(publisher = publisher, repository = repository)

        service.send(message)

        assertThat(publisher.publishedMessages).containsExactly(PublishedMessage("order-events", "20", message))
        val outbox = repository.findByEventId("order-event-1")
        assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PUBLISHED)
        assertThat(outbox?.publishedAt).isNotNull()
    }

    @DisplayName("coupon Kafka 발행에 성공하면 outbox record 를 발행 완료로 변경한다")
    @Test
    fun marksCouponIssueRequestOutboxPublished_whenKafkaSendSucceeds() {
        val message = createCouponIssueRequestMessage()
        val publisher = RecordingExternalEventPublisher()
        val repository = FakeEventOutboxRepository(createCouponIssueRequestOutbox(message))
        val service = createService(publisher = publisher, repository = repository)

        service.send(message)

        assertThat(publisher.publishedMessages)
            .containsExactly(PublishedMessage("coupon-issue-requests", "30", message))
        val outbox = repository.findByEventId("coupon-event-1")
        assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PUBLISHED)
        assertThat(outbox?.publishedAt).isNotNull()
    }

    @DisplayName("Kafka 발행에 실패하면 outbox record 는 미발행 상태로 남긴다")
    @Test
    fun keepsOutboxPending_whenKafkaSendFails() {
        val message = createCatalogMessage()
        val repository = FakeEventOutboxRepository(createCatalogOutbox(message))
        val service = createService(
            publisher = FailingExternalEventPublisher(),
            repository = repository,
        )

        assertThrows<IllegalStateException> {
            service.send(message)
        }

        val outbox = repository.findByEventId("catalog-event-1")
        assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PENDING)
        assertThat(outbox?.publishedAt).isNull()
    }

    private fun createService(
        publisher: ExternalEventPublisher,
        repository: EventOutboxRepository,
    ): ExternalEventSendService {
        return ExternalEventSendService(
            publisher = publisher,
            eventOutboxRepository = repository,
            catalogTopic = "catalog-events",
            orderTopic = "order-events",
            couponIssueRequestTopic = "coupon-issue-requests",
        )
    }

    private fun createCatalogMessage(): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = "catalog-event-1",
            eventType = CatalogEventType.PRODUCT_LIKED,
            aggregateId = 10L,
            productId = 10L,
            brandId = 100L,
            memberId = 1L,
            version = 123L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private fun createOrderMessage(): OrderEventMessage {
        return OrderEventMessage(
            eventId = "order-event-1",
            eventType = OrderEventType.ORDER_CREATED,
            aggregateId = 20L,
            orderId = 20L,
            orderNumber = "order-20",
            memberId = 1L,
            paymentId = null,
            amount = 10_000L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private fun createCouponIssueRequestMessage(): CouponIssueRequestMessage {
        return CouponIssueRequestMessage(
            eventId = "coupon-event-1",
            requestId = "request-1",
            couponId = 30L,
            memberId = 1L,
            requestedAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private fun createCatalogOutbox(message: CatalogEventMessage): EventOutbox {
        return EventOutbox(
            id = 1L,
            eventId = message.eventId,
            topic = "catalog-events",
            partitionKey = message.productId.toString(),
            eventType = message.eventType.name,
            payload = "{}",
        )
    }

    private fun createOrderOutbox(message: OrderEventMessage): EventOutbox {
        return EventOutbox(
            id = 1L,
            eventId = message.eventId,
            topic = "order-events",
            partitionKey = message.orderId.toString(),
            eventType = message.eventType.name,
            payload = "{}",
        )
    }

    private fun createCouponIssueRequestOutbox(message: CouponIssueRequestMessage): EventOutbox {
        return EventOutbox(
            id = 1L,
            eventId = message.eventId,
            topic = "coupon-issue-requests",
            partitionKey = message.couponId.toString(),
            eventType = "COUPON_ISSUE_REQUESTED",
            payload = "{}",
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
        private val records = mutableMapOf(eventOutbox.eventId to eventOutbox)

        override fun save(eventOutbox: EventOutbox): EventOutbox {
            records[eventOutbox.eventId] = eventOutbox
            return eventOutbox
        }

        override fun findByEventId(eventId: String): EventOutbox? {
            return records[eventId]
        }

        override fun findPending(limit: Int): List<EventOutbox> {
            return records.values.take(limit)
        }
    }
}
