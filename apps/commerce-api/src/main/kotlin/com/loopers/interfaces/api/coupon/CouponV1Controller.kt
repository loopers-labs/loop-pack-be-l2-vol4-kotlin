package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.MyCouponsCommand
import com.loopers.application.coupon.usecase.GetCouponIssueResultUsecase
import com.loopers.application.coupon.usecase.GetMyCouponsUsecase
import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.application.coupon.usecase.RequestCouponIssueUsecase
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponV1Controller(
    private val issueCouponUsecase: IssueCouponUsecase,
    private val getMyCouponsUsecase: GetMyCouponsUsecase,
    private val requestCouponIssueUsecase: RequestCouponIssueUsecase,
    private val getCouponIssueResultUsecase: GetCouponIssueResultUsecase,
) {
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    fun issue(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.MyCouponResponse> {
        return issueCouponUsecase.execute(IssueCouponCommand(loginId = loginId, password = password, couponId = couponId))
            .let { CouponV1Dto.MyCouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/api/v1/users/me/coupons")
    fun myCoupons(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<List<CouponV1Dto.MyCouponResponse>> {
        return getMyCouponsUsecase.execute(MyCouponsCommand(loginId = loginId, password = password))
            .map { CouponV1Dto.MyCouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/api/v1/coupons/{couponId}/issue-requests")
    fun requestIssue(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.IssueRequestResponse> =
        requestCouponIssueUsecase.execute(loginId, password, couponId)
            .let { ApiResponse.success(CouponV1Dto.IssueRequestResponse(requestId = it, status = "PENDING")) }

    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    fun issueResult(@PathVariable requestId: String): ApiResponse<CouponV1Dto.IssueResultResponse> =
        getCouponIssueResultUsecase.execute(requestId)
            .let { ApiResponse.success(CouponV1Dto.IssueResultResponse(it.requestId, it.status.name, it.reason)) }
}
