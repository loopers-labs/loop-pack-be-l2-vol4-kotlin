package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader

@Tag(name = "Coupon V1 API", description = "Coupon 도메인 대고객 API 입니다.")
interface CouponV1ApiSpec {
    fun issueCoupon(
        @Schema(name = "로그인 ID") @RequestHeader("X-Loopers-LoginId") loginId: String,
        @Schema(name = "쿠폰 ID", description = "쿠폰의 ID") @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponResponse>

    @Operation(summary = "쿠폰 선착순 발급", description = "선착순으로 쿠폰을 발급합니다.")
    fun requestIssue(
        @Schema(name = "로그인 ID") @RequestHeader("X-Loopers-LoginId") loginId: String,
        @Schema(name = "쿠폰 ID", description = "쿠폰의 ID") @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponIssueRequestResponse>

    @Operation(summary = "쿠폰 목록 조회", description = "리스트로 쿠폰 목록을 조회합니다.")
    fun getCoupons(
        @Schema(name = "로그인 ID") @RequestHeader("X-Loopers-LoginId",
    ) loginId: String): ApiResponse<List<CouponV1Dto.CouponResponse>>
}
