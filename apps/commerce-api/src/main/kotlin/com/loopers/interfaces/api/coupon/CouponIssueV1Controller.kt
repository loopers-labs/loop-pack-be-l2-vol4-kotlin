package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponIssueFacade
import com.loopers.application.user.UserApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.LoginAuth
import com.loopers.support.auth.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/coupons")
class CouponIssueV1Controller(
    private val couponIssueFacade: CouponIssueFacade,
    private val userApplicationService: UserApplicationService,
) : CouponIssueV1ApiSpec {
    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun requestIssue(
        @LoginAuth loginUser: LoginUser,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponIssueV1Dto.IssueResponse> {
        val userInfo = userApplicationService.getUserInfo(
            loginId = loginUser.loginId,
            rawPassword = loginUser.rawPassword,
        )
        val requestId = couponIssueFacade.requestIssue(userInfo.id, couponId)
        return ApiResponse.success(CouponIssueV1Dto.IssueResponse(requestId = requestId))
    }

    @GetMapping("/issue-results/{requestId}")
    override fun getIssueResult(
        @PathVariable requestId: String,
    ): ApiResponse<CouponIssueV1Dto.IssueResultResponse> {
        val info = couponIssueFacade.getIssueResult(requestId)
        return ApiResponse.success(CouponIssueV1Dto.IssueResultResponse.from(info))
    }
}
