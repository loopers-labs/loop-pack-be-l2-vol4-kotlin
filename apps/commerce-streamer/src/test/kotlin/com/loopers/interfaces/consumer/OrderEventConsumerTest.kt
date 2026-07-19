package com.loopers.interfaces.consumer

import com.loopers.application.order.OrderEventService
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
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

class OrderEventConsumerTest {
    private val eventService = mock<OrderEventService>()
    private val acknowledgment = mock<Acknowledgment>()
    private val consumer = OrderEventConsumer(eventService)

    @DisplayName("이벤트 처리에 성공하면 ack 한다")
    @Test
    fun acknowledges_whenMessageIsProcessed() {
        val message = createMessage("event-1")

        consumer.handle(message, acknowledgment)

        verify(eventService).project(message)
        verify(acknowledgment).acknowledge()
    }

    @DisplayName("이벤트 처리에 실패하면 예외를 전파하고 ack 하지 않는다")
    @Test
    fun propagatesException_whenProcessingFails() {
        val message = createMessage("event-1")
        doThrow(IllegalStateException("processing failed"))
            .whenever(eventService)
            .project(message)

        assertThrows<IllegalStateException> {
            consumer.handle(message, acknowledgment)
        }

        verify(eventService).project(message)
        verify(acknowledgment, never()).acknowledge()
    }

    private fun createMessage(eventId: String): OrderEventMessage {
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
}
