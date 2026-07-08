package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponType
import java.time.LocalDateTime

data class CreateCouponCommand(
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long,
    val expiredAt: LocalDateTime,
    val totalCount: Long,
)
