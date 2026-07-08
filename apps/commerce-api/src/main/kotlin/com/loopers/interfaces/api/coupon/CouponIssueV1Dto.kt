package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponIssueResultInfo
import com.loopers.domain.coupon.CouponIssueStatus

class CouponIssueV1Dto {
    data class RequestResponse(
        val requestId: String,
    )

    data class ResultResponse(
        val requestId: String,
        val couponId: Long,
        val status: CouponIssueStatus,
        val reason: String?,
    ) {
        companion object {
            fun from(info: CouponIssueResultInfo): ResultResponse = ResultResponse(
                requestId = info.requestId,
                couponId = info.couponId,
                status = info.status,
                reason = info.reason,
            )
        }
    }
}
