package com.loopers.application.coupon

data class IssueCouponCommand(
    val loginId: String,
    val password: String,
    val couponId: Long,
)

data class MyCouponsCommand(
    val loginId: String,
    val password: String,
)
