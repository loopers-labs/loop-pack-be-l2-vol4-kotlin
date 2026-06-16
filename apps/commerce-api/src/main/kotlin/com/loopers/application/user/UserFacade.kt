package com.loopers.application.user

import com.loopers.application.coupon.CouponService
import com.loopers.application.coupon.dto.CouponIssueInfo
import com.loopers.domain.user.UserSignUpCommand
import org.springframework.stereotype.Component

@Component
class UserFacade(
    private val userService: UserService,
    private val couponService: CouponService,
) {
    fun signUp(command: UserSignUpCommand): UserInfo {
        return userService.signUp(command)
    }

    fun getMe(
        loginId: String,
        rawPassword: String,
    ): UserInfo {
        return userService.getMe(loginId, rawPassword)
    }

    fun getMyCoupons(
        loginId: String,
        rawPassword: String,
    ): List<CouponIssueInfo> {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        return couponService.getCouponIssuesByMemberId(user.id)
            .map(CouponIssueInfo::from)
    }

    fun updatePassword(
        loginId: String,
        rawPassword: String,
        newRawPassword: String,
    ) {
        userService.updatePassword(
            loginId = loginId,
            rawPassword = rawPassword,
            newRawPassword = newRawPassword,
        )
    }
}
