package com.loopers.interfaces.consumer

import com.loopers.application.catalog.CatalogEventService
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.support.Acknowledgment
import java.time.ZonedDateTime

class CatalogEventConsumerTest {
    private val eventService = mock<CatalogEventService>()
    private val acknowledgment = mock<Acknowledgment>()
    private val consumer = CatalogEventConsumer(eventService)

    @DisplayName("이벤트 처리에 성공하면 ack 한다")
    @Test
    fun acknowledges_whenMessageIsProcessed() {
        val message = createMessage("event-1")

        consumer.receive(message, acknowledgment)

        verify(eventService).handle(message)
        verify(acknowledgment).acknowledge()
    }

    @DisplayName("이벤트 처리에 실패하면 예외를 전파하고 ack 하지 않는다")
    @Test
    fun propagatesException_whenProcessingFails() {
        val message = createMessage("event-1")
        doThrow(IllegalStateException("processing failed"))
            .whenever(eventService)
            .handle(message)

        assertThrows<IllegalStateException> {
            consumer.receive(message, acknowledgment)
        }

        verify(eventService).handle(message)
        verify(acknowledgment, never()).acknowledge()
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
}
