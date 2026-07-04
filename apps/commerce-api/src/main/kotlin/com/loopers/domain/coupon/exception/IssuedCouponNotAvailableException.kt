package com.loopers.domain.coupon.exception

import com.loopers.domain.coupon.constant.CouponErrorMessages

class IssuedCouponNotAvailableException : CouponConflictException(CouponErrorMessages.ISSUED_COUPON_NOT_AVAILABLE)
