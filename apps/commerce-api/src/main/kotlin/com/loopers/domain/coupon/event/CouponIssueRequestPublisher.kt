package com.loopers.domain.coupon.event

interface CouponIssueRequestPublisher {
    fun publish(event: CouponIssueRequestEvent.Requested)
}
