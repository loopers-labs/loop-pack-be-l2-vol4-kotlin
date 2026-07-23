package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import java.time.ZonedDateTime

class CouponIssueRequest(
    val id: Long,
    val requestId: String,
    val couponId: Long,
    val memberId: Long,
    status: CouponIssueRequestStatus,
    issueId: Long?,
    reason: String?,
    val requestedAt: ZonedDateTime,
) {
    var status: CouponIssueRequestStatus = status
        private set

    var issueId: Long? = issueId
        private set

    var reason: String? = reason
        private set

    fun issue(issueId: Long) {
        status = CouponIssueRequestStatus.ISSUED
        this.issueId = issueId
        reason = null
    }

    fun reject(reason: String) {
        status = CouponIssueRequestStatus.REJECTED
        this.reason = reason
    }
}
