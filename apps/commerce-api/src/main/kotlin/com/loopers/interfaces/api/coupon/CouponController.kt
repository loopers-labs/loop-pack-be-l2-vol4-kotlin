package com.loopers.interfaces.api.coupon

import com.loopers.domain.auth.AuthService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponApplicationService: CouponApplicationServicePort,
    private val authService: AuthService,
) {
    @PostMapping("/{couponId}/issue")
    fun issueCoupon(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.UserCouponResponse> {
        val userId = authService.login(loginId, loginPw)
        val result = couponApplicationService.issueCoupon(userId, couponId)
        return ApiResponse.success(CouponV1Dto.UserCouponResponse.from(result))
    }
}
