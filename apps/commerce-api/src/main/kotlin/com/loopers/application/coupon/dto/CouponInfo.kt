package com.loopers.application.coupon.dto

import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.domain.coupon.model.Coupon
import java.time.ZonedDateTime

data class CouponInfo(
    val couponId: Long,
    val name: String,
    val type: DiscountType,
    val value: Long,
    val minOrderAmount: Long?,
    val issueLimit: Long?,
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
                coupon.issueLimit,
                coupon.expiredAt,
            )
        }
    }
}
