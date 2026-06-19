package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.Admin
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Admin
@RestController
@RequestMapping("/api-admin/v1/coupons")
class AdminCouponV1Controller(
    private val couponApplicationService: CouponApplicationService,
) : AdminCouponV1ApiSpec {
    @GetMapping
    override fun getCoupons(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<CouponV1Dto.CouponResponse>> =
        couponApplicationService.getAll(page, size)
            .map(CouponV1Dto.CouponResponse::from)
            .let(ApiResponse.Companion::success)

    @GetMapping("/{couponId}")
    override fun getCoupon(
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.CouponResponse> =
        couponApplicationService.get(couponId)
            .let(CouponV1Dto.CouponResponse::from)
            .let(ApiResponse.Companion::success)

    @PostMapping
    override fun createCoupon(
        @RequestBody @Valid request: CouponV1Dto.SaveCouponRequest,
    ): ApiResponse<CouponV1Dto.CouponResponse> =
        couponApplicationService.create(request.toCreateCommand())
            .let(CouponV1Dto.CouponResponse::from)
            .let(ApiResponse.Companion::success)

    @PutMapping("/{couponId}")
    override fun updateCoupon(
        @PathVariable couponId: Long,
        @RequestBody @Valid request: CouponV1Dto.SaveCouponRequest,
    ): ApiResponse<CouponV1Dto.CouponResponse> =
        couponApplicationService.update(couponId, request.toUpdateCommand())
            .let(CouponV1Dto.CouponResponse::from)
            .let(ApiResponse.Companion::success)

    @DeleteMapping("/{couponId}")
    override fun deleteCoupon(
        @PathVariable couponId: Long,
    ): ApiResponse<Unit> {
        couponApplicationService.delete(couponId)
        return ApiResponse.success(Unit)
    }

    @GetMapping("/{couponId}/issues")
    override fun getIssues(
        @PathVariable couponId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<CouponV1Dto.IssuedCouponResponse>> =
        couponApplicationService.getIssues(couponId, page, size)
            .map(CouponV1Dto.IssuedCouponResponse::from)
            .let(ApiResponse.Companion::success)
}
