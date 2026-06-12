package com.loopers.domain.coupon.application.command

import java.time.LocalDateTime

data class CouponTemplateCommand(
    val name: String,
    val type: String,
    val value: Long,
    val minOrderAmount: Long,
    val expiredAt: LocalDateTime,
)
