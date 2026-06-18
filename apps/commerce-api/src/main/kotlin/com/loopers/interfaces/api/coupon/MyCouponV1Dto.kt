package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.MyCouponResult
import java.time.LocalDateTime

class MyCouponV1Dto {
    data class CouponResponse(
        val id: Long,
        val couponId: Long,
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long,
        val status: String,
        val issuedAt: LocalDateTime,
        val usedAt: LocalDateTime?,
        val expiredAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: MyCouponResult): CouponResponse = CouponResponse(
                id = result.id,
                couponId = result.couponTemplateId,
                name = result.name,
                type = result.type.name,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                status = result.status.name,
                issuedAt = result.issuedAt,
                usedAt = result.usedAt,
                expiredAt = result.expiredAt,
            )
        }
    }
}
