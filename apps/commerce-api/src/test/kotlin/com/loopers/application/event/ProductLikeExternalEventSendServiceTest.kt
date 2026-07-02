package com.loopers.application.event

import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.model.EventOutboxStatus
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeExternalEventPublisher
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class ProductLikeExternalEventSendServiceTest {
    @DisplayName("Kafka 발행에 성공하면 outbox record 를 발행 완료로 변경한다")
    @Test
    fun marksOutboxPublished_whenKafkaSendSucceeds() {
        val message = createMessage()
        val publisher = RecordingProductLikeExternalEventPublisher()
        val repository = FakeEventOutboxRepository(
            EventOutbox(
                id = 1L,
                eventId = message.eventId,
                topic = "catalog-events",
                partitionKey = message.productId.toString(),
                eventType = message.eventType.name,
                payload = "{}",
            ),
        )
        val service = ProductLikeExternalEventSendService(
            publisher = publisher,
            eventOutboxRepository = repository,
            catalogTopic = "catalog-events",
        )

        service.send(message)

        assertThat(publisher.publishedMessages).containsExactly(PublishedMessage("catalog-events", "10", message))
        val outbox = repository.findByEventId("event-1")
        assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PUBLISHED)
        assertThat(outbox?.publishedAt).isNotNull()
    }

    @DisplayName("Kafka 발행에 실패하면 outbox record 는 미발행 상태로 남긴다")
    @Test
    fun keepsOutboxPending_whenKafkaSendFails() {
        val message = createMessage()
        val publisher = FailingProductLikeExternalEventPublisher()
        val repository = FakeEventOutboxRepository(
            EventOutbox(
                id = 1L,
                eventId = message.eventId,
                topic = "catalog-events",
                partitionKey = message.productId.toString(),
                eventType = message.eventType.name,
                payload = "{}",
            ),
        )
        val service = ProductLikeExternalEventSendService(
            publisher = publisher,
            eventOutboxRepository = repository,
            catalogTopic = "catalog-events",
        )

        assertThrows<IllegalStateException> {
            service.send(message)
        }

        val outbox = repository.findByEventId("event-1")
        assertThat(outbox?.status).isEqualTo(EventOutboxStatus.PENDING)
        assertThat(outbox?.publishedAt).isNull()
    }

    private fun createMessage(): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = "event-1",
            eventType = CatalogEventType.PRODUCT_LIKED,
            aggregateId = 10L,
            productId = 10L,
            brandId = 100L,
            memberId = 1L,
            version = 123L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private data class PublishedMessage(
        val topic: String,
        val partitionKey: String,
        val message: CatalogEventMessage,
    )

    private class RecordingProductLikeExternalEventPublisher : ProductLikeExternalEventPublisher {
        val publishedMessages = mutableListOf<PublishedMessage>()

        override fun publish(
            topic: String,
            partitionKey: String,
            message: CatalogEventMessage,
        ) {
            publishedMessages.add(PublishedMessage(topic, partitionKey, message))
        }
    }

    private class FailingProductLikeExternalEventPublisher : ProductLikeExternalEventPublisher {
        override fun publish(
            topic: String,
            partitionKey: String,
            message: CatalogEventMessage,
        ) {
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
