package com.loopers.domain.coupon.exception

import com.loopers.domain.coupon.constant.CouponErrorMessages

class CouponNotOwnedException : CouponDomainException(CouponErrorMessages.COUPON_NOT_OWNED)
