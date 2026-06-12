package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Coupon V1 API", description = "Loopers admin coupon API.")
interface AdminCouponV1ApiSpec {
    @Operation(summary = "쿠폰 템플릿 목록", description = "쿠폰 템플릿 목록을 조회합니다.")
    fun getCoupons(page: Int, size: Int): ApiResponse<List<CouponV1Dto.CouponResponse>>

    @Operation(summary = "쿠폰 템플릿 상세", description = "쿠폰 템플릿 상세를 조회합니다.")
    fun getCoupon(couponId: Long): ApiResponse<CouponV1Dto.CouponResponse>

    @Operation(summary = "쿠폰 템플릿 등록", description = "정액 또는 정률 쿠폰 템플릿을 등록합니다.")
    fun createCoupon(request: CouponV1Dto.SaveCouponRequest): ApiResponse<CouponV1Dto.CouponResponse>

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿을 수정합니다.")
    fun updateCoupon(couponId: Long, request: CouponV1Dto.SaveCouponRequest): ApiResponse<CouponV1Dto.CouponResponse>

    @Operation(summary = "쿠폰 템플릿 삭제", description = "쿠폰 템플릿을 soft delete 합니다.")
    fun deleteCoupon(couponId: Long): ApiResponse<Unit>

    @Operation(summary = "쿠폰 발급 내역", description = "특정 쿠폰 템플릿의 발급 내역을 조회합니다.")
    fun getIssues(couponId: Long, page: Int, size: Int): ApiResponse<List<CouponV1Dto.IssuedCouponResponse>>
}
