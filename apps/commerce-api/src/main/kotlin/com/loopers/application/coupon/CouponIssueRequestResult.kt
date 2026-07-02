package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueFailureReason
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueStatus

data class CouponIssueRequestResult(
    val id: Long,
    val userId: Long,
    val couponTemplateId: Long,
    val status: CouponIssueStatus,
    val failureReason: CouponIssueFailureReason?,
) {
    companion object {
        fun from(request: CouponIssueRequest): CouponIssueRequestResult = CouponIssueRequestResult(
            id = request.id,
            userId = request.userId,
            couponTemplateId = request.couponTemplateId,
            status = request.status,
            failureReason = request.failureReason,
        )
    }
}
