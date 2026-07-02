package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime
import java.util.UUID

class CouponIssueRequest(
    val id: Long = 0L,
    val requestId: String,
    val couponId: Long,
    val memberId: Long,
    status: CouponIssueRequestStatus,
    issueId: Long? = null,
    reason: String? = null,
    val requestedAt: ZonedDateTime,
) {
    var status: CouponIssueRequestStatus = status
        private set

    var issueId: Long? = issueId
        private set

    var reason: String? = reason
        private set

    init {
        if (requestId.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon issue request id must not be blank.")
        }
        if (couponId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon id must be positive.")
        }
        if (memberId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Member id must be positive.")
        }
    }

    fun issue(issueId: Long) {
        status = CouponIssueRequestStatus.ISSUED
        this.issueId = issueId
        reason = null
    }

    fun reject(reason: String) {
        status = CouponIssueRequestStatus.REJECTED
        this.reason = reason
    }

    fun fail(reason: String) {
        status = CouponIssueRequestStatus.FAILED
        this.reason = reason
    }

    companion object {
        fun requested(
            couponId: Long,
            memberId: Long,
            requestId: String = UUID.randomUUID().toString(),
            requestedAt: ZonedDateTime = ZonedDateTime.now(),
        ): CouponIssueRequest {
            return CouponIssueRequest(
                requestId = requestId,
                couponId = couponId,
                memberId = memberId,
                status = CouponIssueRequestStatus.REQUESTED,
                requestedAt = requestedAt,
            )
        }
    }
}
