package com.loopers.interfaces.consumer

import com.loopers.application.coupon.CouponFacade
import com.loopers.application.coupon.CouponIssueRequestPayload
import com.loopers.config.kafka.KafkaConfig
import com.loopers.config.kafka.KafkaTopics.COUPON_ISSUE_REQUESTS
import com.loopers.configuration.DeadLetterConfig
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponEventConsumer(
    private val couponFacade: CouponFacade,
) {
    @KafkaListener(
        topics = [COUPON_ISSUE_REQUESTS],
        groupId = "coupon-issue",
        containerFactory = DeadLetterConfig.SINGLE_LISTENER,
    )
    fun consume(message: CouponIssueRequestPayload, ack: Acknowledgment) {
        couponFacade.issue(message)
        ack.acknowledge()
    }
}
