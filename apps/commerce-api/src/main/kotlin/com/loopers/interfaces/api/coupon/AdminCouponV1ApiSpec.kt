package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Coupon V1 API", description = "관리자 쿠폰 API 입니다.")
interface AdminCouponV1ApiSpec {
    @Operation(
        summary = "관리자 쿠폰 템플릿 목록 조회",
        description = "관리자가 쿠폰 템플릿 목록을 페이지로 조회합니다.",
    )
    fun getCoupons(
        adminId: String,
        page: Int,
        size: Int,
    ): ApiResponse<PageResponse<AdminCouponV1Dto.CouponResponse>>

    @Operation(
        summary = "관리자 쿠폰 템플릿 상세 조회",
        description = "관리자가 쿠폰 템플릿 상세를 조회합니다.",
    )
    fun getCoupon(
        adminId: String,
        couponId: Long,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse>

    @Operation(
        summary = "관리자 쿠폰 템플릿 등록",
        description = "관리자가 쿠폰 템플릿을 등록합니다. 정액(FIXED)/정률(RATE) 타입 지정.)",
    )
    fun createCoupon(
        adminId: String,
        request: AdminCouponV1Dto.CreateCouponRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse>
}
