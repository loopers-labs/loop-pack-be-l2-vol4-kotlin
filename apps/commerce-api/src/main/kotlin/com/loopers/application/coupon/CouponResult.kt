package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponType
import java.time.LocalDateTime

data class CouponResult(
    val id: Long,
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long,
    val expiredAt: LocalDateTime,
) {
    companion object {
        fun from(couponTemplate: CouponTemplate): CouponResult = CouponResult(
            id = couponTemplate.id,
            name = couponTemplate.name,
            type = couponTemplate.type,
            value = couponTemplate.value,
            minOrderAmount = couponTemplate.minOrderAmount,
            expiredAt = couponTemplate.expiredAt,
        )
    }
}
