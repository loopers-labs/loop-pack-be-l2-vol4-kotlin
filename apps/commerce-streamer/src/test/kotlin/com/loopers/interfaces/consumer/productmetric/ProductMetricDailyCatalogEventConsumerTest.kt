package com.loopers.interfaces.consumer.productmetric

import com.loopers.application.productmetric.ProductMetricDailyEventService
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.NonRetryableEventException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import java.time.ZonedDateTime

class ProductMetricDailyCatalogEventConsumerTest {
    private val service = mock<ProductMetricDailyEventService>()
    private val acknowledgment = mock<Acknowledgment>()
    private val consumer = ProductMetricDailyCatalogEventConsumer(service)

    @DisplayName("Catalog metric batch 처리에 성공하면 ack 한다")
    @Test
    fun acknowledges_whenMessagesAreProcessed() {
        val messages = listOf(createMessage("event-1"), createMessage("event-2"))

        consumer.receive(messages, acknowledgment)

        verify(service).handle(messages[0])
        verify(service).handle(messages[1])
        verify(acknowledgment).acknowledge()
    }

    @DisplayName("Catalog metric batch 중 non-retryable 실패가 발생하면 실패 index를 전파하고 ack 하지 않는다")
    @Test
    fun propagatesBatchFailureIndex_whenNonRetryableExceptionOccurs() {
        val messages = listOf(createMessage("event-1"), createMessage("event-2"))
        doThrow(NonRetryableEventException("invalid event"))
            .whenever(service)
            .handle(messages[1])

        val exception = assertThrows<BatchListenerFailedException> {
            consumer.receive(messages, acknowledgment)
        }

        verify(acknowledgment, never()).acknowledge()
        assert(exception.index == 1)
    }

    private fun createMessage(eventId: String): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = eventId,
            eventType = CatalogEventType.PRODUCT_VIEWED,
            aggregateId = 10L,
            productId = 10L,
            brandId = 100L,
            memberId = 1L,
            version = 100L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }
}
