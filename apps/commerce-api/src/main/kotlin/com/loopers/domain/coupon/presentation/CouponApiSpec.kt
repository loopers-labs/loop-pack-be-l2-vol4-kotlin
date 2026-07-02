package com.loopers.domain.coupon.presentation

import com.loopers.domain.coupon.presentation.response.IssuedCouponResponse
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon API", description = "Loopers 쿠폰 API 입니다.")
interface CouponApiSpec {
    @Operation(
        summary = "쿠폰 발급",
        description = "로그인 사용자가 쿠폰 템플릿을 발급받습니다.",
    )
    fun issueCoupon(
        user: UserInfo,
        couponTemplateId: Long,
    ): ApiResponse<IssuedCouponResponse>

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "로그인 사용자가 보유한 쿠폰과 표시 상태를 조회합니다.",
    )
    fun findMyCoupons(user: UserInfo): ApiResponse<List<IssuedCouponResponse>>
}
