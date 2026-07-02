package com.loopers.domain.coupon.exception

import com.loopers.domain.coupon.constant.CouponErrorMessages

class DuplicateIssuedCouponException(
    cause: Throwable? = null,
) : CouponConflictException(CouponErrorMessages.DUPLICATE_ISSUED_COUPON, cause)
