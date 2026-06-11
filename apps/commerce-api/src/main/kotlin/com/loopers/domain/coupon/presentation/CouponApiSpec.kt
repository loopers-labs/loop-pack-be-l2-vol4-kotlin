package com.loopers.domain.coupon.presentation

import com.loopers.domain.coupon.presentation.request.CouponTemplateRequest
import com.loopers.domain.coupon.presentation.response.CouponTemplateResponse
import com.loopers.domain.coupon.presentation.response.IssuedCouponResponse
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Min

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

@Tag(name = "Coupon Admin API", description = "Loopers 쿠폰 관리자 API 입니다.")
interface CouponAdminApiSpec {
    @Operation(
        summary = "쿠폰 템플릿 목록 조회",
        description = "관리자가 쿠폰 템플릿 목록을 조회합니다.",
    )
    fun findTemplates(
        ldap: String?,
        @Min(0)
        page: Int?,
        @Min(1)
        size: Int?,
    ): ApiResponse<List<CouponTemplateResponse>>

    @Operation(
        summary = "쿠폰 템플릿 상세 조회",
        description = "관리자가 쿠폰 템플릿 상세를 조회합니다.",
    )
    fun findTemplate(
        ldap: String?,
        couponTemplateId: Long,
    ): ApiResponse<CouponTemplateResponse>

    @Operation(
        summary = "쿠폰 템플릿 생성",
        description = "관리자가 정액 또는 정률 쿠폰 템플릿을 생성합니다.",
    )
    fun createTemplate(
        ldap: String?,
        @Valid request: CouponTemplateRequest,
    ): ApiResponse<CouponTemplateResponse>

    @Operation(
        summary = "쿠폰 템플릿 수정",
        description = "관리자가 쿠폰 템플릿 정책을 수정합니다.",
    )
    fun updateTemplate(
        ldap: String?,
        couponTemplateId: Long,
        @Valid request: CouponTemplateRequest,
    ): ApiResponse<CouponTemplateResponse>

    @Operation(
        summary = "쿠폰 템플릿 삭제",
        description = "관리자가 쿠폰 템플릿을 삭제합니다.",
    )
    fun deleteTemplate(
        ldap: String?,
        couponTemplateId: Long,
    ): ApiResponse<Any>

    @Operation(
        summary = "쿠폰 발급 이력 조회",
        description = "관리자가 특정 쿠폰 템플릿의 발급 이력을 조회합니다.",
    )
    fun findIssuedCouponsByTemplate(
        ldap: String?,
        couponTemplateId: Long,
        @Min(0)
        page: Int?,
        @Min(1)
        size: Int?,
    ): ApiResponse<List<IssuedCouponResponse>>
}
