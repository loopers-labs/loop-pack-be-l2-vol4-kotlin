package com.loopers.interfaces.api.coupon

import com.loopers.domain.auth.AuthService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponApplicationService: CouponApplicationServicePort,
    private val authService: AuthService,
) {
    @PostMapping("/{couponId}/issue")
    fun issueCoupon(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.IssueRequestResponse> {
        val userId = authService.login(loginId, loginPw)
        val result = couponApplicationService.issueCoupon(userId, couponId)
        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(result))
    }

    @GetMapping("/{couponId}/issue/status")
    fun getIssueStatus(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.IssueStatusResponse> {
        val userId = authService.login(loginId, loginPw)
        val result = couponApplicationService.getIssueStatus(userId, couponId)
        return ApiResponse.success(CouponV1Dto.IssueStatusResponse.from(result))
    }
}
