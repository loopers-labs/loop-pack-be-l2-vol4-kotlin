package com.loopers.domain.coupon

import java.time.LocalDateTime

class CouponCommand {
    data class Create(
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    )

    data class Update(
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    )
}
