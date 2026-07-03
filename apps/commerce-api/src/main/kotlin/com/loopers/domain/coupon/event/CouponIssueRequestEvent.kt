package com.loopers.domain.coupon.event

import com.loopers.domain.coupon.model.CouponIssueRequest
import java.time.ZonedDateTime

object CouponIssueRequestEvent {
    data class Requested(
        val requestId: String,
        val couponId: Long,
        val memberId: Long,
        val eventId: String = requestId,
        val requestedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(request: CouponIssueRequest): Requested {
                return Requested(
                    requestId = request.requestId,
                    couponId = request.couponId,
                    memberId = request.memberId,
                    requestedAt = request.requestedAt,
                )
            }
        }
    }
}
