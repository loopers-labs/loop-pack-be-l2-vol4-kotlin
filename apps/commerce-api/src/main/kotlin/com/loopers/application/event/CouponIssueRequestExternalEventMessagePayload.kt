package com.loopers.application.event

import com.loopers.domain.coupon.event.CouponIssueRequestEvent
import com.loopers.event.CouponIssueRequestMessage

object CouponIssueRequestExternalEventMessagePayload {
    fun from(event: CouponIssueRequestEvent.Requested): CouponIssueRequestMessage {
        return CouponIssueRequestMessage(
            eventId = event.eventId,
            requestId = event.requestId,
            couponId = event.couponId,
            memberId = event.memberId,
            requestedAt = event.requestedAt,
        )
    }
}
