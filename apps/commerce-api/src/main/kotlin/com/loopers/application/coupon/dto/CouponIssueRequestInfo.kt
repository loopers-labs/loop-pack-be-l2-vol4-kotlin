package com.loopers.application.coupon.dto

import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import com.loopers.domain.coupon.model.CouponIssueRequest
import java.time.ZonedDateTime

data class CouponIssueRequestInfo(
    val requestId: String,
    val couponId: Long,
    val memberId: Long,
    val status: CouponIssueRequestStatus,
    val issueId: Long?,
    val reason: String?,
    val requestedAt: ZonedDateTime,
) {
    companion object {
        fun from(request: CouponIssueRequest): CouponIssueRequestInfo {
            return CouponIssueRequestInfo(
                requestId = request.requestId,
                couponId = request.couponId,
                memberId = request.memberId,
                status = request.status,
                issueId = request.issueId,
                reason = request.reason,
                requestedAt = request.requestedAt,
            )
        }
    }
}
