package com.loopers.domain.coupon.exception

open class CouponDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
