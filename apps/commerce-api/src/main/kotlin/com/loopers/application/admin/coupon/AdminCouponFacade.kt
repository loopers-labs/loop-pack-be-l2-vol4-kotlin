package com.loopers.application.admin.coupon

import com.loopers.application.coupon.CouponService
import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import org.springframework.stereotype.Component

@Component
class AdminCouponFacade(
    private val couponService: CouponService,
) {
    fun createCoupon(command: CouponCreateCommand): CouponInfo {
        return couponService.createCoupon(command)
            .let(CouponInfo::from)
    }
}
