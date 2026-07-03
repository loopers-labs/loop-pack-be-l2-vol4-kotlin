package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueStatus

data class CouponIssueResultInfo(
    val requestId: String,
    val couponId: Long,
    val status: CouponIssueStatus,
    val reason: String?,
) {
    companion object {
        fun from(request: CouponIssueRequestModel): CouponIssueResultInfo = CouponIssueResultInfo(
            requestId = request.requestId,
            couponId = request.couponId,
            status = request.status,
            reason = request.reason,
        )
    }
}
