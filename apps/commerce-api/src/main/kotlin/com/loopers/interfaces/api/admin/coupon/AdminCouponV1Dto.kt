package com.loopers.interfaces.api.admin.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

class AdminCouponV1Dto {
    data class CreateCouponRequest(
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
    ) {
        fun toCommand(): CouponCreateCommand {
            return CouponCreateCommand(
                name,
                type,
                value,
                minOrderAmount,
                expiredAt,
            )
        }
    }

    data class CouponResponse(
        val couponId: Long,
        val name: String,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: CouponInfo): CouponResponse {
                return CouponResponse(
                    info.couponId,
                    info.name,
                    info.value,
                    info.minOrderAmount,
                    info.expiredAt,
                )
            }
        }
    }
}
