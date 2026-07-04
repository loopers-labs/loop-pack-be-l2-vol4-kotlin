package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueStatus

data class CouponIssueRequestInfo (
    val requestId: String,
    val status: CouponIssueStatus,
) {
    companion object {
        fun from(model: CouponIssueRequestModel) =
            CouponIssueRequestInfo(requestId = model.requestId, status = model.status)
    }
}
