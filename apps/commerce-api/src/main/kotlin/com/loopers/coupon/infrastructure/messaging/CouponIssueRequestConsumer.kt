package com.loopers.coupon.infrastructure.messaging

import com.loopers.config.CouponKafkaListenerConfig
import com.loopers.coupon.application.CouponIssueProcessor
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.support.error.CoreException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestConsumer(
    private val couponIssueProcessor: CouponIssueProcessor,
) {
    @KafkaListener(
        topics = [CouponIssueRequestKafkaPublisher.COUPON_ISSUE_REQUESTS_TOPIC],
        groupId = "coupon-issue-processor",
        containerFactory = CouponKafkaListenerConfig.COUPON_ISSUE_LISTENER,
        autoStartup = "\${coupon.issue-consumer.auto-startup:true}",
    )
    fun consume(couponIssueRequestEvent: CouponIssueRequestEvent, acknowledgment: Acknowledgment) {
        if (couponIssueProcessor.register(couponIssueRequestEvent)) {
            try {
                couponIssueProcessor.approve(couponIssueRequestEvent)
            } catch (coreException: CoreException) {
                couponIssueProcessor.reject(couponIssueRequestEvent.requestId, coreException.errorCode.code)
            } catch (dataIntegrityViolationException: DataIntegrityViolationException) {
                couponIssueProcessor.reject(couponIssueRequestEvent.requestId, CouponErrorCode.ALREADY_ISSUED.code)
            }
        }
        acknowledgment.acknowledge()
    }
}
