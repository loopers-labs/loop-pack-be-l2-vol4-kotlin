package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponResult
import com.loopers.application.coupon.CreateCouponCommand
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface CouponAdminApplicationServicePort {
    fun createCoupon(command: CreateCouponCommand): CouponResult
    fun getCoupons(pageRequest: PageRequest): PageResult<CouponResult>
}
