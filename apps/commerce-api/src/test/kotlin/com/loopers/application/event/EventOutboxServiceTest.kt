package com.loopers.application.event

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.event.model.EventOutbox
import com.loopers.domain.event.repository.EventOutboxRepository
import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.ZonedDateTime

class EventOutboxServiceTest {
    @DisplayName("좋아요 이벤트를 catalog outbox record 로 저장한다")
    @Test
    fun recordsLikedEvent() {
        val repository = RecordingEventOutboxRepository()
        val objectMapper = jacksonObjectMapper().findAndRegisterModules()
        val service = EventOutboxService(
            eventOutboxRepository = repository,
            objectMapper = objectMapper,
            catalogTopic = "catalog-events",
        )
        val occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00")

        service.record(
            ProductLikeEvent.Like(
                memberId = 1L,
                productId = 10L,
                brandId = 100L,
                eventId = "event-1",
                occurredAt = occurredAt,
                version = 123L,
            ),
        )

        val outbox = repository.events.single()
        val payload = objectMapper.readValue<CatalogEventMessage>(outbox.payload)
        assertAll(
            { assertThat(outbox.eventId).isEqualTo("event-1") },
            { assertThat(outbox.topic).isEqualTo("catalog-events") },
            { assertThat(outbox.partitionKey).isEqualTo("10") },
            { assertThat(outbox.eventType).isEqualTo(CatalogEventType.PRODUCT_LIKED.name) },
            { assertThat(payload.eventType).isEqualTo(CatalogEventType.PRODUCT_LIKED) },
            { assertThat(payload.productId).isEqualTo(10L) },
            { assertThat(payload.memberId).isEqualTo(1L) },
            { assertThat(payload.version).isEqualTo(123L) },
        )
    }

    private class RecordingEventOutboxRepository : EventOutboxRepository {
        val events = mutableListOf<EventOutbox>()

        override fun save(eventOutbox: EventOutbox): EventOutbox {
            events.add(eventOutbox)
            return eventOutbox
        }

        override fun findPending(limit: Int): List<EventOutbox> {
            return events.take(limit)
        }
    }
}
