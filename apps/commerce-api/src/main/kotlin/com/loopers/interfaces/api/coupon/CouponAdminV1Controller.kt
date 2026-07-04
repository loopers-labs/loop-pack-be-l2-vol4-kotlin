package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.AdminCouponFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
class CouponAdminV1Controller(
    private val adminCouponFacade: AdminCouponFacade,
) : CouponAdminV1ApiSpec {
    @GetMapping
    override fun getCoupons(pageable: Pageable): ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> {
        val page = adminCouponFacade.getCoupons(pageable)
        val response = PageResponse.from(page.map { CouponAdminV1Dto.CouponResponse.from(it) })
        return ApiResponse.success(response)
    }

    @GetMapping("/{couponId}")
    override fun getCoupon(@PathVariable couponId: Long): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = adminCouponFacade.getCoupon(couponId)
        val response = CouponAdminV1Dto.CouponResponse.from(info)
        return ApiResponse.success(response)
    }

    @PostMapping
    override fun createCoupon(
        @RequestBody request: CouponAdminV1Dto.CreateCouponRequest
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = adminCouponFacade.createCoupon(
            name = request.name,
            type = request.type.toDomain(),
            value = request.value,
            minOrderAmount = request.minOrderAmount,
            expiredAt = request.expiredAt,
            quantity = request.quantity,
        )
        val response = CouponAdminV1Dto.CouponResponse.from(info)
        return ApiResponse.success(response)
    }

    @PutMapping("/{couponId}")
    override fun updateCoupon(
        @PathVariable couponId: Long,
        @RequestBody request: CouponAdminV1Dto.UpdateCouponRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = adminCouponFacade.updateCoupon(couponId, request.name, request.expiredAt)
        val response = CouponAdminV1Dto.CouponResponse.from(info)
        return ApiResponse.success(response)
    }

    @DeleteMapping("/{couponId}")
    override fun deleteCoupon(@PathVariable couponId: Long): ApiResponse<Any> {
        adminCouponFacade.deleteCoupon(couponId)
        return ApiResponse.success()
    }

    @GetMapping("/{couponId}/issues")
    override fun getCouponHistory(
        @PathVariable couponId: Long,
        pageable: Pageable,
    ): ApiResponse<PageResponse<CouponAdminV1Dto.CouponHistoryResponse>> {
        val page = adminCouponFacade.getCouponHistory(couponId, pageable)
        val response = PageResponse.from(page.map { CouponAdminV1Dto.CouponHistoryResponse.from(it) })
        return ApiResponse.success(response)
    }
}
