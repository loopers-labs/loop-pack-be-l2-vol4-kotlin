package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.coupon.dto.CouponV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon V1 API", description = "쿠폰 API 입니다.")
interface CouponV1ApiSpec {
    @Operation(
        summary = "쿠폰 발급",
        description = "로그인한 회원에게 쿠폰을 발급합니다.",
    )
    fun issueCoupon(
        loginId: String,
        password: String,
        couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponIssueResponse>
}
