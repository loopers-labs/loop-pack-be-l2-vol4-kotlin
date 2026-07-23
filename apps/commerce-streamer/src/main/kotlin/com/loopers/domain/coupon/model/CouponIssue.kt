package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.enums.CouponIssueStatus
import com.loopers.domain.coupon.enums.DiscountType
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
    companion object {
        fun issue(
            memberId: Long,
            coupon: Coupon,
        ): CouponIssue {
            return CouponIssue(
                memberId = memberId,
                couponId = coupon.id,
                status = CouponIssueStatus.AVAILABLE,
                type = coupon.type,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                usedAt = null,
            )
        }
    }
}
