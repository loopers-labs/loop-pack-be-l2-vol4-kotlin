package com.loopers.domain.coupon.presentation.response

import com.loopers.domain.coupon.application.info.CouponIssueRequestInfo
import java.util.UUID

data class CouponIssueRequestResponse(
    val requestId: UUID,
    val status: String,
    val issuedCouponId: Long?,
    val failureReason: String?,
) {
    companion object {
        fun from(info: CouponIssueRequestInfo): CouponIssueRequestResponse =
            CouponIssueRequestResponse(
                requestId = info.requestId,
                status = info.status.name,
                issuedCouponId = info.issuedCouponId,
                failureReason = info.failureReason,
            )
    }
}
