package com.loopers.application.coupon.dto

import com.loopers.domain.coupon.enums.DiscountType
import java.time.ZonedDateTime

data class CouponCreateCommand(
    val name: String,
    val type: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
)
