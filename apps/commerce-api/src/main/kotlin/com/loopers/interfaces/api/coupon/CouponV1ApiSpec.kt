package com.loopers.interfaces.api.coupon

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon V1 API", description = "Loopers coupon API.")
interface CouponV1ApiSpec {
    @Operation(summary = "쿠폰 발급", description = "인증된 사용자에게 쿠폰을 발급합니다.")
    fun issue(user: User, couponId: Long): ApiResponse<CouponV1Dto.IssuedCouponResponse>

    @Operation(summary = "내 쿠폰 목록", description = "인증된 사용자의 쿠폰 목록과 상태를 조회합니다.")
    fun getMyCoupons(user: User): ApiResponse<List<CouponV1Dto.IssuedCouponResponse>>
}
