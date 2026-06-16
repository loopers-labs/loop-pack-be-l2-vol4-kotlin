package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.domain.coupon.CouponIssueDisplayStatus
import com.loopers.domain.coupon.DiscountType
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
}
