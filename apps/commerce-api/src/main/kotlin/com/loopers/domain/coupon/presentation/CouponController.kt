package com.loopers.domain.coupon.presentation

import com.loopers.domain.coupon.application.CouponFacade
import com.loopers.domain.coupon.presentation.response.CouponIssueRequestResponse
import com.loopers.domain.coupon.presentation.response.IssuedCouponResponse
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.presentation.auth.LoginUser
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Validated
class CouponController(
    private val couponFacade: CouponFacade,
) : CouponApiSpec {
    @PostMapping("/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    override fun issueCoupon(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @PathVariable("couponId") couponTemplateId: Long,
    ): ApiResponse<CouponIssueRequestResponse> =
        couponFacade.requestIssue(user.id, couponTemplateId)
            .let { CouponIssueRequestResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/coupons/issue-requests/{requestId}")
    override fun findIssueRequest(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @PathVariable requestId: UUID,
    ): ApiResponse<CouponIssueRequestResponse> =
        couponFacade.findIssueRequest(user.id, requestId)
            .let { CouponIssueRequestResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/users/me/coupons")
    override fun findMyCoupons(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
    ): ApiResponse<List<IssuedCouponResponse>> =
        couponFacade.findMyCoupons(user.id)
            .map { IssuedCouponResponse.from(it) }
            .let { ApiResponse.success(it) }
}
