package com.loopers.application.coupon.dto

import com.loopers.domain.coupon.DiscountType
import java.time.ZonedDateTime

data class CouponUpdateCommand(
    val name: String,
    val type: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
)
