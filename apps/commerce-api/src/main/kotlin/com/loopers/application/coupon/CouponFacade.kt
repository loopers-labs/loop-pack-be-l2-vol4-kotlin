package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import org.springframework.stereotype.Component

@Component
class CouponFacade(
    private val couponService: CouponService,
) {
    fun createCoupon(command: CouponCreateCommand): CouponInfo {
        return couponService.createCoupon(command)
            .let(CouponInfo::from)
    }
}
