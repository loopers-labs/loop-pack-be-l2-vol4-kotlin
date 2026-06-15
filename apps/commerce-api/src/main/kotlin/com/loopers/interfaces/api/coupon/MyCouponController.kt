package com.loopers.interfaces.api.coupon

import com.loopers.domain.auth.AuthService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 대고객 "내 쿠폰" 엔드포인트. URL 은 사용자 하위 리소스(/api/v1/users/me/coupons)로 노출하지만,
 * 다루는 도메인은 coupon 이므로 coupon 패키지에 둔다.
 */
@RestController
@RequestMapping("/api/v1/users/me/coupons")
class MyCouponController(
    private val couponApplicationService: CouponApplicationServicePort,
    private val authService: AuthService,
) {
    @GetMapping
    fun getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
    ): ApiResponse<List<MyCouponV1Dto.CouponResponse>> {
        val userId = authService.login(loginId, loginPw)
        val results = couponApplicationService.getMyCoupons(userId)
        return ApiResponse.success(results.map { MyCouponV1Dto.CouponResponse.from(it) })
    }
}
