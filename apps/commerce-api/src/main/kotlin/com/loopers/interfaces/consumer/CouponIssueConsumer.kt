package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.application.coupon.CouponIssueProcessor
import com.loopers.application.event.EventTopic
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class CouponIssueConsumer(
    private val couponIssueProcessor: CouponIssueProcessor,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [EventTopic.COUPON_ISSUE_REQUESTS_VALUE],
        groupId = "coupon-issue-consumer",
        containerFactory = KafkaConfig.SINGLE_LISTENER_WITH_DLT,
    )
    fun consume(record: ConsumerRecord<String, ByteArray>) {
        val eventId = record.headers().lastHeader("eventId")
            ?.value()?.let { String(it, Charsets.UTF_8) }
            ?: throw IllegalArgumentException("eventId header is required. topic=${record.topic()}, offset=${record.offset()}")

        val eventType = record.headers().lastHeader("eventType")
            ?.value()?.let { String(it, Charsets.UTF_8) }
            ?: throw IllegalArgumentException("eventType header is required. topic=${record.topic()}, offset=${record.offset()}")

        val payload: CouponIssueRequestedPayload = objectMapper.readValue(String(record.value(), Charsets.UTF_8))

        couponIssueProcessor.process(
            eventId = eventId,
            eventType = eventType,
            requestId = payload.requestId,
            userId = payload.userId,
            couponId = payload.couponId,
        )
    }
}

private data class CouponIssueRequestedPayload(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
)
