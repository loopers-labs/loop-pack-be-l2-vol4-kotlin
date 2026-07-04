package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Transactional
@Component
class AdminCouponFacade(
    private val couponService: CouponService,
    private val userCouponService: UserCouponService,
) {
    fun createCoupon(name: String, type: CouponType, value: Double, minOrderAmount: Double, expiredAt: ZonedDateTime, quantity: Int): AdminCouponInfo {
        val couponModel = couponService.createCoupon(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            quantity = quantity,
        )
        return AdminCouponInfo.from(couponModel)
    }

    fun getCoupon(couponId: Long): AdminCouponInfo {
        return couponService.getCoupon(couponId)
            .let { AdminCouponInfo.from(it) }
    }

    fun getCoupons(pageable: Pageable): Page<AdminCouponInfo> {
        return couponService.getCoupons(pageable)
            .map { AdminCouponInfo.from(it) }
    }

    fun getCouponHistory(couponId: Long, pageable: Pageable): Page<UserCouponInfo> {
        return userCouponService.getAllByCouponId(couponId, pageable)
            .map { UserCouponInfo.from(it) }
    }

    fun updateCoupon(couponId: Long, name: String, expiredAt: ZonedDateTime): AdminCouponInfo {
        val coupon = couponService.getCoupon(couponId)
        coupon.update(name, expiredAt)
        return AdminCouponInfo.from(coupon)
    }

    fun deleteCoupon(couponId: Long) {
        couponService.getCoupon(couponId).delete()
    }
}
