package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponV1Controller(
    private val issueCouponUsecase: IssueCouponUsecase,
) {
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    fun issue(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.MyCouponResponse> {
        return issueCouponUsecase.execute(IssueCouponCommand(loginId = loginId, password = password, couponId = couponId))
            .let { CouponV1Dto.MyCouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
