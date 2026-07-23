package com.loopers.interfaces.consumer

import com.loopers.application.coupon.CouponIssueRequestProcessor
import com.loopers.event.CouponIssueRequestMessage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.kafka.support.Acknowledgment
import java.time.ZonedDateTime

class CouponIssueRequestConsumerTest {
    private val processor = mock<CouponIssueRequestProcessor>()
    private val acknowledgment = mock<Acknowledgment>()
    private val consumer = CouponIssueRequestConsumer(processor)

    @DisplayName("쿠폰 발급 요청 처리에 성공하면 ack 한다")
    @Test
    fun acknowledges_whenMessageIsHandled() {
        val message = createMessage()

        consumer.receive(message, acknowledgment)

        verify(processor).handle(message)
        verify(acknowledgment).acknowledge()
    }

    @DisplayName("쿠폰 발급 요청 처리에 실패하면 ack 하지 않는다")
    @Test
    fun doesNotAcknowledge_whenHandlingFails() {
        val message = createMessage()
        doThrow(IllegalStateException("processing failed"))
            .`when`(processor)
            .handle(message)

        assertThrows<IllegalStateException> {
            consumer.receive(message, acknowledgment)
        }

        verify(acknowledgment, never()).acknowledge()
    }

    private fun createMessage(): CouponIssueRequestMessage {
        return CouponIssueRequestMessage(
            eventId = "event-1",
            requestId = "request-1",
            couponId = 1L,
            memberId = 1L,
            requestedAt = ZonedDateTime.parse("2026-07-20T10:00:00+09:00"),
        )
    }
}
