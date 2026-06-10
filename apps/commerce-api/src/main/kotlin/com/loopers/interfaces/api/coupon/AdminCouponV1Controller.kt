package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
class AdminCouponV1Controller(
    private val couponFacade: CouponFacade,
) : AdminCouponV1ApiSpec {
    @PostMapping
    override fun createCoupon(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestBody request: AdminCouponV1Dto.CreateCouponRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return couponFacade.createCoupon(request.toCommand())
            .let(AdminCouponV1Dto.CouponResponse::from)
            .let { ApiResponse.success(it) }
    }
}
