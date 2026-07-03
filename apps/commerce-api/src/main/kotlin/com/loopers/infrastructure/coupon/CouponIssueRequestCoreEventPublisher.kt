package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.event.CouponIssueRequestEvent
import com.loopers.domain.coupon.event.CouponIssueRequestPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestCoreEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : CouponIssueRequestPublisher {
    override fun publish(event: CouponIssueRequestEvent.Requested) {
        applicationEventPublisher.publishEvent(event)
    }
}
