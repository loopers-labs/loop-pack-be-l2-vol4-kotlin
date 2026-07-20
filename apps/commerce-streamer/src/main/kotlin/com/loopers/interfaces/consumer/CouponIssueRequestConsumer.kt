package com.loopers.interfaces.consumer

import com.loopers.application.coupon.CouponIssueRequestProcessor
import com.loopers.config.kafka.KafkaConfig
import com.loopers.event.CouponIssueRequestMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.DltStrategy
import org.springframework.kafka.support.Acknowledgment
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestConsumer(
    private val couponIssueRequestProcessor: CouponIssueRequestProcessor,
) {
    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000),
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
        kafkaTemplate = "kafkaTemplate",
    )
    @KafkaListener(
        topics = ["\${commerce.events.coupon-issue-request-topic:coupon-issue-requests}"],
        groupId = "\${commerce.coupon.consumer-group:commerce-coupon-issue}",
        containerFactory = KafkaConfig.SINGLE_LISTENER,
    )
    fun receive(
        message: CouponIssueRequestMessage,
        acknowledgment: Acknowledgment,
    ) {
        couponIssueRequestProcessor.handle(message)
        acknowledgment.acknowledge()
    }
}
