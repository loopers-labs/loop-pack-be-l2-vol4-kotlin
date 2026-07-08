package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponIssueRequestResult

class CouponV1Dto {
    data class IssueRequestResponse(
        val issueRequestId: Long,
        val couponId: Long,
        val status: String,
    ) {
        companion object {
            fun from(result: CouponIssueRequestResult): IssueRequestResponse = IssueRequestResponse(
                issueRequestId = result.id,
                couponId = result.couponTemplateId,
                status = result.status.name,
            )
        }
    }

    data class IssueStatusResponse(
        val status: String,
        val failureReason: String?,
    ) {
        companion object {
            fun from(result: CouponIssueRequestResult): IssueStatusResponse = IssueStatusResponse(
                status = result.status.name,
                failureReason = result.failureReason?.name,
            )
        }
    }
}
