package com.loopers.domain.coupon.unit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.config.kafka.CouponIssueTopicProperties
import com.loopers.domain.coupon.application.service.CouponIssueRequestFailureService
import com.loopers.domain.coupon.presentation.CouponIssueDeadLetterHandler
import com.loopers.domain.coupon.presentation.CouponIssueRequestKafkaEvent
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CouponIssueDeadLetterHandlerTest {
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val failureService = mockk<CouponIssueRequestFailureService>(relaxed = true)
    private val properties = CouponIssueTopicProperties(
        topicName = "configured-coupon-requests",
        dltTopicName = "configured-coupon-failures",
        partitions = 3,
        replicas = 1,
    )
    private val handler = CouponIssueDeadLetterHandler(properties, objectMapper, failureService)

    @Test
    fun `설정된_쿠폰_요청_주제를_설정된_DLT로_복구한다`() {
        val requestId = UUID.randomUUID()
        val event = CouponIssueRequestKafkaEvent(
            eventId = UUID.randomUUID(),
            eventType = "COUPON_ISSUE_REQUESTED_V1",
            aggregateType = "COUPON_ISSUE_REQUEST",
            aggregateId = 91L,
            payload = """{"requestId":"$requestId"}""",
        )
        val record = ConsumerRecord(
            properties.topicName,
            2,
            0L,
            "42",
            objectMapper.writeValueAsBytes(event),
        )
        val exception = IllegalStateException("retry exhausted")

        assertThat(handler.supports(record)).isTrue()
        assertThat(handler.destination(record, exception).topic()).isEqualTo(properties.dltTopicName)
        assertThat(handler.destination(record, exception).partition()).isEqualTo(2)

        handler.afterPublished(record, exception)

        verify(exactly = 1) { failureService.markFailed(requestId, "retry exhausted") }
    }
}
