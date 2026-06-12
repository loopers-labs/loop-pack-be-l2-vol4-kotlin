package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponType
import java.math.BigDecimal
import java.time.ZonedDateTime

data class UpsertCouponCommand(
    val name: String,
    val type: CouponType,
    val discountValue: BigDecimal,
    val minOrderAmount: BigDecimal?,
    val expiredAt: ZonedDateTime,
)
