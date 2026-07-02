package com.loopers.domain.coupon.event

import com.loopers.event.CouponIssueRequestMessage

interface CouponIssueRequestPublisher {
    fun publish(message: CouponIssueRequestMessage)
}
