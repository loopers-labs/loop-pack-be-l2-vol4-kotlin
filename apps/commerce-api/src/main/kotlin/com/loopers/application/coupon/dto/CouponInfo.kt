package com.loopers.application.coupon.dto

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

data class CouponInfo(
    val couponId: Long,
    val name: String,
    val type: DiscountType,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
) {
    companion object {
        fun from(coupon: Coupon): CouponInfo {
            return CouponInfo(
                coupon.id,
                coupon.name,
                coupon.type,
                coupon.discountValue,
                coupon.minOrderAmount,
                coupon.expiredAt,
            )
        }
    }
}
