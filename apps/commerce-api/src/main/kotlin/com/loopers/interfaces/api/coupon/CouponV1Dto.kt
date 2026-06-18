package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.MyCouponInfo
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

class CouponV1Dto {
    data class MyCouponResponse(
        val id: Long,
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: BigDecimal,
        val minOrderAmount: BigDecimal?,
        val expiredAt: ZonedDateTime,
        val status: UserCouponStatus,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: MyCouponInfo): MyCouponResponse {
                return MyCouponResponse(
                    id = info.id,
                    couponId = info.couponId,
                    name = info.name,
                    type = info.type,
                    value = info.discountValue,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                    status = info.status,
                    usedAt = info.usedAt,
                )
            }
        }
    }
}
