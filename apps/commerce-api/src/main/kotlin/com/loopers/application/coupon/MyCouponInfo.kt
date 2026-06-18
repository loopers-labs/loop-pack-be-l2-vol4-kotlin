package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

data class MyCouponInfo(
    val id: Long,
    val couponId: Long,
    val name: String,
    val type: CouponType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val expiredAt: ZonedDateTime,
    val status: UserCouponStatus,
    val usedAt: ZonedDateTime?,
) {
    companion object {
        fun from(userCoupon: UserCouponModel, coupon: CouponModel, now: ZonedDateTime): MyCouponInfo {
            return MyCouponInfo(
                id = userCoupon.id,
                couponId = coupon.id,
                name = coupon.name,
                type = coupon.type,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                status = userCoupon.currentStatus(coupon = coupon, now = now),
                usedAt = userCoupon.usedAt,
            )
        }
    }
}
