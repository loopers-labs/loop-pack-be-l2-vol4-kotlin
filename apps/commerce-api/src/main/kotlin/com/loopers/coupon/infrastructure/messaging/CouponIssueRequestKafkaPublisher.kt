package com.loopers.coupon.infrastructure.messaging

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestKafkaPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    fun publish(couponIssueRequestEvent: CouponIssueRequestEvent) {
        kafkaTemplate.send(
            COUPON_ISSUE_REQUESTS_TOPIC,
            couponIssueRequestEvent.couponId.toString(),
            couponIssueRequestEvent,
        ).get(10, TimeUnit.SECONDS)
    }

    companion object {
        const val COUPON_ISSUE_REQUESTS_TOPIC = "coupon-issue-requests"
    }
}

data class CouponIssueRequestEvent(
    val requestId: String,
    val couponId: Long,
    val userId: Long,
    val requestedAt: LocalDateTime,
)
