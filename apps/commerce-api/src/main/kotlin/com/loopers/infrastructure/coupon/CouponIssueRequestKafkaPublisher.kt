package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.event.CouponIssueRequestPublisher
import com.loopers.event.CouponIssueRequestMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestKafkaPublisher(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    @Value("\${commerce.events.coupon-issue-request-topic:coupon-issue-requests}")
    private val topic: String,
) : CouponIssueRequestPublisher {
    override fun publish(message: CouponIssueRequestMessage) {
        kafkaTemplate.send(topic, message.couponId.toString(), message).get()
    }
}
