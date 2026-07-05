package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponInfo
import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.application.coupon.dto.CouponIssueRequestInfo
import com.loopers.application.coupon.dto.CouponUpdateCommand
import com.loopers.application.user.UserService
import com.loopers.domain.coupon.event.CouponIssueRequestEvent
import com.loopers.domain.coupon.event.CouponIssueRequestPublisher
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponFacade(
    private val couponService: CouponService,
    private val couponIssueRequestService: CouponIssueRequestService,
    private val couponIssueRequestPublisher: CouponIssueRequestPublisher,
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

    @Transactional
    fun issueCoupon(
        loginId: String,
        rawPassword: String,
        couponId: Long,
    ): CouponIssueRequestInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        val request = couponIssueRequestService.createRequested(memberId = user.id, couponId = couponId)
        couponIssueRequestPublisher.publish(CouponIssueRequestEvent.Requested.from(request))

        return CouponIssueRequestInfo.from(request)
    }

    fun getIssueRequest(
        loginId: String,
        rawPassword: String,
        requestId: String,
    ): CouponIssueRequestInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        return couponIssueRequestService.getOwnedRequest(requestId = requestId, memberId = user.id)
            .let(CouponIssueRequestInfo::from)
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
