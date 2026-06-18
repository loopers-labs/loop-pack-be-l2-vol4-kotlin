package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.usecase.admin.CreateCouponUsecase
import com.loopers.application.coupon.usecase.admin.DeleteCouponUsecase
import com.loopers.application.coupon.usecase.admin.GetCouponIssuesUsecase
import com.loopers.application.coupon.usecase.admin.GetCouponUsecase
import com.loopers.application.coupon.usecase.admin.GetCouponsUsecase
import com.loopers.application.coupon.usecase.admin.UpdateCouponUsecase
import com.loopers.interfaces.api.ApiResponse
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
class AdminCouponV1Controller(
    private val createCouponUsecase: CreateCouponUsecase,
    private val updateCouponUsecase: UpdateCouponUsecase,
    private val deleteCouponUsecase: DeleteCouponUsecase,
    private val getCouponUsecase: GetCouponUsecase,
    private val getCouponsUsecase: GetCouponsUsecase,
    private val getCouponIssuesUsecase: GetCouponIssuesUsecase,
) {
    @GetMapping
    fun list(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<AdminCouponV1Dto.CouponPageResponse> {
        authorize(ldap)
        return getCouponsUsecase.execute(page = page, size = size)
            .let { AdminCouponV1Dto.CouponPageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{couponId}")
    fun detail(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        authorize(ldap)
        return getCouponUsecase.execute(couponId)
            .let { AdminCouponV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    fun create(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @RequestBody request: AdminCouponV1Dto.CouponUpsertRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        authorize(ldap)
        return createCouponUsecase.execute(request.toCommand())
            .let { AdminCouponV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{couponId}")
    fun update(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
        @RequestBody request: AdminCouponV1Dto.CouponUpsertRequest,
    ): ApiResponse<AdminCouponV1Dto.CouponResponse> {
        authorize(ldap)
        return updateCouponUsecase.execute(couponId = couponId, command = request.toCommand())
            .let { AdminCouponV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{couponId}")
    fun delete(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
    ): ApiResponse<Any> {
        authorize(ldap)
        deleteCouponUsecase.execute(couponId)
        return ApiResponse.success()
    }

    @GetMapping("/{couponId}/issues")
    fun issues(
        @RequestHeader(value = LDAP_HEADER, required = false) ldap: String?,
        @PathVariable couponId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<AdminCouponV1Dto.CouponIssuePageResponse> {
        authorize(ldap)
        return getCouponIssuesUsecase.execute(couponId = couponId, page = page, size = size)
            .let { AdminCouponV1Dto.CouponIssuePageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    private fun authorize(ldap: String?) {
        if (ldap.isNullOrBlank()) throw CoreException(ErrorType.UNAUTHORIZED, "관리자 인증이 필요합니다.")
    }

    companion object {
        private const val LDAP_HEADER = "X-Loopers-Ldap"
    }
}
