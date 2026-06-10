package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

class CouponIssue(
    val id: Long = 0L,
    val memberId: Long,
    val couponId: Long,
    val status: CouponIssueStatus,
    val type: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
    val usedAt: ZonedDateTime?,
) {
    init {
        if (memberId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Member id must be positive.")
        }
        if (couponId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon id must be positive.")
        }
        if (minOrderAmount != null && minOrderAmount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "MinimumOrderAmount cannot be less than zero.")
        }
    }

    fun displayStatusAt(now: ZonedDateTime): CouponIssueDisplayStatus {
        if (status == CouponIssueStatus.USED) {
            return CouponIssueDisplayStatus.USED
        }

        if (!expiredAt.isAfter(now)) {
            return CouponIssueDisplayStatus.EXPIRED
        }

        return CouponIssueDisplayStatus.AVAILABLE
    }
}
