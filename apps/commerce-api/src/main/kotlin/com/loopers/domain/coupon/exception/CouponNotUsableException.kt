package com.loopers.domain.coupon.exception

class CouponNotUsableException(
    message: String,
) : CouponConflictException(message)
