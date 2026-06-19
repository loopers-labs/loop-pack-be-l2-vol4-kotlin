package com.loopers.application.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponStatus
import java.time.LocalDateTime

class CouponInfo {
    data class Template(
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        companion object {
            fun from(coupon: Coupon) = Template(
                couponId = coupon.id,
                name = coupon.name,
                type = coupon.type,
                value = coupon.value,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
            )
        }
    }

    data class Issued(
        val issueId: Long,
        val userId: Long,
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
        val status: IssuedCouponStatus,
    ) {
        companion object {
            fun from(issue: IssuedCoupon, coupon: Coupon, now: LocalDateTime) = Issued(
                issueId = issue.id,
                userId = issue.userId,
                couponId = coupon.id,
                name = coupon.name,
                type = coupon.type,
                value = coupon.value,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                status = issue.effectiveStatus(coupon, now),
            )
        }
    }

    data class Applied(
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val totalAmount: Long,
        val discountAmount: Long,
        val paymentAmount: Long,
    )
}
