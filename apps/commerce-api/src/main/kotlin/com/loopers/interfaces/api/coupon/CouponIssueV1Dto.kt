package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponIssueResultInfo
import com.loopers.domain.coupon.CouponIssueStatus

class CouponIssueV1Dto {
    data class IssueResponse(
        val requestId: String,
    )

    data class IssueResultResponse(
        val requestId: String,
        val status: CouponIssueStatus,
        val reason: String?,
    ) {
        companion object {
            fun from(info: CouponIssueResultInfo): IssueResultResponse =
                IssueResultResponse(
                    requestId = info.requestId,
                    status = info.status,
                    reason = info.reason,
                )
        }
    }
}
