package com.loopers.domain.coupon.exception

class CouponNotIssuableException(
    message: String,
) : CouponConflictException(message)
