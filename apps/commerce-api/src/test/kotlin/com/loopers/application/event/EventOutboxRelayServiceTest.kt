package com.loopers.application.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.model.EventOutboxStatus
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeExternalEventPublisher
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
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
        val publisher = RecordingProductLikeExternalEventPublisher()
        val service = EventOutboxRelayService(
            eventOutboxRepository = repository,
            publisher = publisher,
            objectMapper = objectMapper,
        )

        val relayedCount = service.relayPending(limit = 100)

        val outbox = repository.findByEventId("event-1")
        val publishedMessage = publisher.publishedMessages.single()
        assertAll(
            { assertThat(relayedCount).isEqualTo(1) },
            { assertThat(publishedMessage.topic).isEqualTo("catalog-events") },
            { assertThat(publishedMessage.partitionKey).isEqualTo("10") },
            { assertThat(publishedMessage.message.eventId).isEqualTo(message.eventId) },
            { assertThat(publishedMessage.message.eventType).isEqualTo(message.eventType) },
            { assertThat(publishedMessage.message.productId).isEqualTo(message.productId) },
            { assertThat(publishedMessage.message.occurredAt.toInstant()).isEqualTo(message.occurredAt.toInstant()) },
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
            publisher = FailingProductLikeExternalEventPublisher(),
            objectMapper = objectMapper,
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
