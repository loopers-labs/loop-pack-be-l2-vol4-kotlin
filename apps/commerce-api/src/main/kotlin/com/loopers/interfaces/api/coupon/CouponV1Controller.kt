package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponApplicationService
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@LoginRequired
@RestController
@RequestMapping("/api/v1")
class CouponV1Controller(
    private val couponApplicationService: CouponApplicationService,
) : CouponV1ApiSpec {
    @PostMapping("/coupons/{couponId}/issue")
    override fun issue(
        @CurrentUser user: User,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.IssuedCouponResponse> =
        couponApplicationService.issue(userId = user.id, couponId = couponId)
            .let(CouponV1Dto.IssuedCouponResponse::from)
            .let(ApiResponse.Companion::success)

    @GetMapping("/users/me/coupons")
    override fun getMyCoupons(
        @CurrentUser user: User,
    ): ApiResponse<List<CouponV1Dto.IssuedCouponResponse>> =
        couponApplicationService.getMyCoupons(user.id)
            .map(CouponV1Dto.IssuedCouponResponse::from)
            .let(ApiResponse.Companion::success)
}
