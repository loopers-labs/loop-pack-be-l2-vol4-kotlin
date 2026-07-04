package com.loopers.domain.coupon.application.info

import com.loopers.domain.coupon.model.CouponIssueRequestModel
import com.loopers.domain.coupon.model.CouponIssueRequestStatus
import java.util.UUID

data class CouponIssueRequestInfo(
    val requestId: UUID,
    val status: CouponIssueRequestStatus,
    val issuedCouponId: Long?,
    val failureReason: String?,
) {
    companion object {
        fun from(request: CouponIssueRequestModel): CouponIssueRequestInfo =
            CouponIssueRequestInfo(
                requestId = request.requestId,
                status = request.status,
                issuedCouponId = request.issuedCouponId,
                failureReason = request.failureReason,
            )
    }
}
