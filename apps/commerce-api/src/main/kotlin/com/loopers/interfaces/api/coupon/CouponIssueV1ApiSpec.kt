package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Coupon Issue", description = "쿠폰 발급 API")
interface CouponIssueV1ApiSpec {
    @Operation(summary = "쿠폰 발급 요청", description = "선착순 쿠폰 발급을 요청한다. 비동기로 처리되며 requestId를 반환한다.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestIssue(
        @LoginAuth loginUser: LoginUser,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponIssueV1Dto.IssueResponse>

    @Operation(summary = "쿠폰 발급 결과 조회", description = "requestId로 쿠폰 발급 결과를 조회한다.")
    fun getIssueResult(
        @PathVariable requestId: String,
    ): ApiResponse<CouponIssueV1Dto.IssueResultResponse>
}
