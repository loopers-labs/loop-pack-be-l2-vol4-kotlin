package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponType
import java.time.LocalDateTime

data class UpdateCouponCommand(
    val id: Long,
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long,
    val expiredAt: LocalDateTime,
)
