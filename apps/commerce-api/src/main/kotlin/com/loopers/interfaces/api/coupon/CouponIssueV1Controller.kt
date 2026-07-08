package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponIssueFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponIssueV1Controller(
    private val couponIssueFacade: CouponIssueFacade,
) : CouponIssueV1ApiSpec {
    @PostMapping("/api/v1/coupons/{couponId}/issue-requests")
    override fun requestIssue(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponIssueV1Dto.RequestResponse> {
        val requestId = couponIssueFacade.requestIssue(loginId, loginPw, couponId)
        return ApiResponse.success(CouponIssueV1Dto.RequestResponse(requestId))
    }

    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    override fun getResult(
        @PathVariable requestId: String,
    ): ApiResponse<CouponIssueV1Dto.ResultResponse> {
        val result = couponIssueFacade.getResult(requestId)
        return ApiResponse.success(CouponIssueV1Dto.ResultResponse.from(result))
    }
}
