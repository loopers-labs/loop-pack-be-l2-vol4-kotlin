package com.loopers.domain.coupon.exception

open class CouponConflictException(
    message: String,
    cause: Throwable? = null,
) : CouponDomainException(message, cause)
