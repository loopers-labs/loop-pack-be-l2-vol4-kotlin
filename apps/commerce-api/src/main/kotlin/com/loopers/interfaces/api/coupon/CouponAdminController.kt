package com.loopers.interfaces.api.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.common.PageView
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
class CouponAdminController(
    private val couponApplicationService: CouponAdminApplicationServicePort,
) {
    @GetMapping
    fun getCoupons(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
    ): ApiResponse<PageView<CouponAdminV1Dto.CouponResponse>> {
        verifyAdmin(ldap)
        val result = couponApplicationService.getCoupons(PageRequest(page = page, size = size))
        return ApiResponse.success(PageView.from(result, CouponAdminV1Dto.CouponResponse::from))
    }

    @GetMapping("/{id}")
    fun getCoupon(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        verifyAdmin(ldap)
        val result = couponApplicationService.getCoupon(id)
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(result))
    }

    @PostMapping
    fun createCoupon(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestBody request: CouponAdminV1Dto.CreateCouponRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        verifyAdmin(ldap)
        val result = couponApplicationService.createCoupon(request.toCommand())
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(result))
    }

    @PutMapping("/{id}")
    fun updateCoupon(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
        @RequestBody request: CouponAdminV1Dto.UpdateCouponRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        verifyAdmin(ldap)
        val result = couponApplicationService.updateCoupon(request.toCommand(id))
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(result))
    }

    @DeleteMapping("/{id}")
    fun deleteCoupon(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
    ): ApiResponse<Any> {
        verifyAdmin(ldap)
        couponApplicationService.deleteCoupon(id)
        return ApiResponse.success()
    }

    private fun verifyAdmin(ldap: String?) {
        if (ldap != ADMIN_LDAP) {
            throw CoreException(ErrorType.FORBIDDEN, "어드민 권한이 없습니다.")
        }
    }

    companion object {
        private const val ADMIN_LDAP = "loopers.admin"
    }
}
