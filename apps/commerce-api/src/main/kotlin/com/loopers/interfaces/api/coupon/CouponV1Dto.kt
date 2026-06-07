package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.UserCouponResult
import java.time.LocalDateTime

class CouponV1Dto {
    data class UserCouponResponse(
        val id: Long,
        val couponId: Long,
        val status: String,
        val issuedAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: UserCouponResult): UserCouponResponse = UserCouponResponse(
                id = result.id,
                couponId = result.couponTemplateId,
                status = result.status.name,
                issuedAt = result.issuedAt,
            )
        }
    }
}
