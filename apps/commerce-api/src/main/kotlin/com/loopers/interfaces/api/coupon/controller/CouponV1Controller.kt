package com.loopers.interfaces.api.coupon.controller

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.coupon.CouponV1ApiSpec
import com.loopers.interfaces.api.coupon.dto.CouponV1Dto
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class CouponV1Controller(
    private val couponFacade: CouponFacade,
) : CouponV1ApiSpec {
    @PostMapping("/coupons/{couponId}/issue")
    override fun issueCoupon(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponIssueRequestResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return couponFacade.issueCoupon(
            loginId = loginId,
            rawPassword = password,
            couponId = couponId,
        ).let(CouponV1Dto.CouponIssueRequestResponse::from)
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/coupons/issue-requests/{requestId}")
    override fun getIssueRequest(
        @RequestHeader(LoopersHeaders.LOGIN_ID) loginId: String,
        @RequestHeader(LoopersHeaders.LOGIN_PW) password: String,
        @PathVariable requestId: String,
    ): ApiResponse<CouponV1Dto.CouponIssueRequestResponse> {
        LoopersHeaders.validateUser(loginId = loginId, password = password)

        return couponFacade.getIssueRequest(
            loginId = loginId,
            rawPassword = password,
            requestId = requestId,
        ).let(CouponV1Dto.CouponIssueRequestResponse::from)
            .let { ApiResponse.success(it) }
    }
}
