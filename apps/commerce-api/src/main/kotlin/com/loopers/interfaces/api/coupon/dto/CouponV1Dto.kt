package com.loopers.interfaces.api.coupon.dto

import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.application.coupon.dto.CouponIssueRequestInfo
import com.loopers.domain.coupon.enums.CouponIssueDisplayStatus
import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import com.loopers.domain.coupon.enums.DiscountType
import java.time.ZonedDateTime

class CouponV1Dto {
    data class CouponIssueResponse(
        val issueId: Long,
        val couponId: Long,
        val memberId: Long,
        val status: CouponIssueDisplayStatus,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: CouponIssueInfo): CouponIssueResponse {
                return CouponIssueResponse(
                    issueId = info.issueId,
                    couponId = info.couponId,
                    memberId = info.memberId,
                    status = info.status,
                    type = info.type,
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                    usedAt = info.usedAt,
                )
            }
        }
    }

    data class CouponIssueRequestResponse(
        val requestId: String,
        val couponId: Long,
        val memberId: Long,
        val status: CouponIssueRequestStatus,
        val issueId: Long?,
        val reason: String?,
        val requestedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: CouponIssueRequestInfo): CouponIssueRequestResponse {
                return CouponIssueRequestResponse(
                    requestId = info.requestId,
                    couponId = info.couponId,
                    memberId = info.memberId,
                    status = info.status,
                    issueId = info.issueId,
                    reason = info.reason,
                    requestedAt = info.requestedAt,
                )
            }
        }
    }
}
