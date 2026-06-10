package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import com.loopers.application.coupon.dto.CouponUpdateCommand
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class CouponFacade(
    private val couponService: CouponService,
) {
    fun getCoupon(couponId: Long): CouponInfo {
        return couponService.getCoupon(couponId)
            .let(CouponInfo::from)
    }

    fun getCoupons(page: Int, size: Int): Page<CouponInfo> {
        return couponService.getCoupons(page = page, size = size)
            .map(CouponInfo::from)
    }

    fun createCoupon(command: CouponCreateCommand): CouponInfo {
        return couponService.createCoupon(command)
            .let(CouponInfo::from)
    }

    fun updateCoupon(couponId: Long, command: CouponUpdateCommand): CouponInfo {
        return couponService.updateCoupon(couponId = couponId, command = command)
            .let(CouponInfo::from)
    }
}
