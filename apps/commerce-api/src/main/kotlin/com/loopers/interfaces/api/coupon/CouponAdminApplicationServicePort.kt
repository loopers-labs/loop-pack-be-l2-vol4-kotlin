package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponResult
import com.loopers.application.coupon.CreateCouponCommand

interface CouponAdminApplicationServicePort {
    fun createCoupon(command: CreateCouponCommand): CouponResult
}
