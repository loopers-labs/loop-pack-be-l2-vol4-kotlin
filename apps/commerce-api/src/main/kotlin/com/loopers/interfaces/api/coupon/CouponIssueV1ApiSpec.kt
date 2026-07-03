package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader

@Tag(name = "Coupon Issue V1", description = "선착순 쿠폰 발급(비동기) API")
interface CouponIssueV1ApiSpec {
    @Operation(summary = "선착순 쿠폰 발급 요청 (비동기)", description = "요청을 접수하고 requestId 를 즉시 반환한다. 실제 발급은 순차 처리된다.")
    fun requestIssue(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponIssueV1Dto.RequestResponse>

    @Operation(summary = "발급 요청 결과 조회", description = "PENDING / SUCCESS / FAILED 를 조회한다.")
    fun getResult(
        @PathVariable requestId: String,
    ): ApiResponse<CouponIssueV1Dto.ResultResponse>
}
