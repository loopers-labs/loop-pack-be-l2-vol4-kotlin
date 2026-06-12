package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

data class AdminCouponInfo(
    val id: Long,
    val name: String,
    val type: CouponType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val expiredAt: ZonedDateTime,
) {
    companion object {
        fun from(coupon: CouponModel): AdminCouponInfo {
            return AdminCouponInfo(
                id = coupon.id,
                name = coupon.name,
                type = coupon.type,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
            )
        }
    }
}

data class CouponIssueInfo(
    val id: Long,
    val userId: Long,
    val status: UserCouponStatus,
    val issuedAt: ZonedDateTime,
    val usedAt: ZonedDateTime?,
) {
    companion object {
        fun from(userCoupon: UserCouponModel, coupon: CouponModel, now: ZonedDateTime): CouponIssueInfo {
            return CouponIssueInfo(
                id = userCoupon.id,
                userId = userCoupon.userId,
                status = userCoupon.currentStatus(coupon = coupon, now = now),
                issuedAt = userCoupon.createdAt,
                usedAt = userCoupon.usedAt,
            )
        }
    }
}
