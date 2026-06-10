package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.support.LoopersHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
class AdminCouponV1Controller(
    private val couponFacade: CouponFacade,
) : AdminCouponV1ApiSpec {
    @GetMapping
    override fun getCoupons(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<AdminCouponV1Dto.CouponResponse>> {
        LoopersHeaders.validateAdmin(adminId)

        return couponFacade.getCoupons(page = page, size = size)
            .map(AdminCouponV1Dto.CouponResponse::from)
            .let { PageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{couponId}")
    override fun getCoupon(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable couponId: Long,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return couponFacade.getCoupon(couponId)
            .let(AdminCouponV1Dto.CouponResponse::from)
            .let { ApiResponse.success(it) }
    }

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

    @PutMapping("/{couponId}")
    override fun updateCoupon(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable couponId: Long,
        @RequestBody request: AdminCouponV1Dto.UpdateCouponRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return couponFacade.updateCoupon(couponId = couponId, command = request.toCommand())
            .let(AdminCouponV1Dto.CouponResponse::from)
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{couponId}")
    override fun deleteCoupon(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable couponId: Long,
    ): ApiResponse<Any> {
        LoopersHeaders.validateAdmin(adminId)

        couponFacade.deleteCoupon(couponId)
        return ApiResponse.success()
    }
}
