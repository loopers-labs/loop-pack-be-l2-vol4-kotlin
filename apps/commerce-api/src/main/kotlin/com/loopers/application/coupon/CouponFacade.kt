package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.application.coupon.dto.CouponUpdateCommand
import com.loopers.application.user.UserService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class CouponFacade(
    private val couponService: CouponService,
    private val userService: UserService,
) {
    fun getCoupon(couponId: Long): CouponInfo {
        return couponService.getCoupon(couponId)
            .let(CouponInfo::from)
    }

    fun getCoupons(page: Int, size: Int): Page<CouponInfo> {
        return couponService.getCoupons(page = page, size = size)
            .map(CouponInfo::from)
    }

    fun getCouponIssues(couponId: Long, page: Int, size: Int): Page<CouponIssueInfo> {
        return couponService.getCouponIssues(couponId = couponId, page = page, size = size)
            .map(CouponIssueInfo::from)
    }

    fun issueCoupon(
        loginId: String,
        rawPassword: String,
        couponId: Long,
    ): CouponIssueInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        return couponService.issueCoupon(memberId = user.id, couponId = couponId)
            .let(CouponIssueInfo::from)
    }

    fun createCoupon(command: CouponCreateCommand): CouponInfo {
        return couponService.createCoupon(command)
            .let(CouponInfo::from)
    }

    fun updateCoupon(couponId: Long, command: CouponUpdateCommand): CouponInfo {
        return couponService.updateCoupon(couponId = couponId, command = command)
            .let(CouponInfo::from)
    }

    fun deleteCoupon(couponId: Long) {
        couponService.deleteCoupon(couponId)
    }
}
