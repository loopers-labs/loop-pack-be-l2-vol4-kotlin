package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.UserCoupon
import java.time.LocalDateTime

data class UserCouponResult(
    val id: Long,
    val couponTemplateId: Long,
    val userId: Long,
    val status: CouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun from(userCoupon: UserCoupon): UserCouponResult = UserCouponResult(
            id = userCoupon.id,
            couponTemplateId = userCoupon.couponTemplateId,
            userId = userCoupon.userId,
            status = userCoupon.status,
            issuedAt = userCoupon.issuedAt,
            usedAt = userCoupon.usedAt,
        )
    }
}
