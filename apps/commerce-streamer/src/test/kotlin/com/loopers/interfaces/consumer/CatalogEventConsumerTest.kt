package com.loopers.interfaces.consumer

import com.loopers.application.catalog.CatalogEventProjectionService
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
    private val projectionService = mock<CatalogEventProjectionService>()
    private val acknowledgment = mock<Acknowledgment>()
    private val consumer = CatalogEventConsumer(projectionService)

    @DisplayName("이벤트 처리에 성공하면 ack 한다")
    @Test
    fun acknowledges_whenMessageIsProjected() {
        val message = createMessage("event-1")

        consumer.handle(message, acknowledgment)

        verify(projectionService).project(message)
        verify(acknowledgment).acknowledge()
    }

    @DisplayName("이벤트 처리에 실패하면 예외를 전파하고 ack 하지 않는다")
    @Test
    fun propagatesException_whenProjectionFails() {
        val message = createMessage("event-1")
        doThrow(IllegalStateException("projection failed"))
            .whenever(projectionService)
            .project(message)

        assertThrows<IllegalStateException> {
            consumer.handle(message, acknowledgment)
        }

        verify(projectionService).project(message)
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
