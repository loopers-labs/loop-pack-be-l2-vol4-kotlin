package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
class CouponAdminController(
    private val couponApplicationService: CouponAdminApplicationServicePort,
) {
    @PostMapping
    fun createCoupon(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestBody request: CouponAdminV1Dto.CreateCouponRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        verifyAdmin(ldap)
        val result = couponApplicationService.createCoupon(request.toCommand())
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(result))
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
