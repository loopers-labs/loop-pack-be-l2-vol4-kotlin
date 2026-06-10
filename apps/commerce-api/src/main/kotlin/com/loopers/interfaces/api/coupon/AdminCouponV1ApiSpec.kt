package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Coupon V1 API", description = "관리자 쿠폰 API 입니다.")
interface AdminCouponV1ApiSpec {
    @Operation(
        summary = "관리자 쿠폰 템플릿 등록",
        description = "관리자가 쿠폰 템플릿을 등록합니다. 정액(FIXED)/정률(RATE) 타입 지정.)",
    )
    fun createCoupon(
        adminId: String,
        request: AdminCouponV1Dto.CreateCouponRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse>
}
