package com.loopers.domain.coupon.presentation

import com.loopers.domain.coupon.application.CouponFacade
import com.loopers.domain.coupon.presentation.request.CouponTemplateRequest
import com.loopers.domain.coupon.presentation.response.CouponTemplateResponse
import com.loopers.domain.coupon.presentation.response.IssuedCouponResponse
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
@Validated
class CouponAdminController(
    private val couponFacade: CouponFacade,
) : CouponAdminApiSpec {
    @GetMapping
    override fun findTemplates(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<CouponTemplateResponse>> {
        requireAdmin(ldap)
        val result = couponFacade.findTemplates(page ?: DEFAULT_PAGE, size ?: DEFAULT_SIZE)
            .map { CouponTemplateResponse.from(it) }
        return ApiResponse.success(PageResponse.from(result))
    }

    @GetMapping("/{couponId}")
    override fun findTemplate(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @PathVariable("couponId") couponTemplateId: Long,
    ): ApiResponse<CouponTemplateResponse> {
        requireAdmin(ldap)
        return couponFacade.findTemplate(couponTemplateId)
            .let { CouponTemplateResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun createTemplate(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @Valid @RequestBody request: CouponTemplateRequest,
    ): ApiResponse<CouponTemplateResponse> {
        requireAdmin(ldap)
        return couponFacade.createTemplate(request.toCommand())
            .let { CouponTemplateResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{couponId}")
    override fun updateTemplate(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @PathVariable("couponId") couponTemplateId: Long,
        @Valid @RequestBody request: CouponTemplateRequest,
    ): ApiResponse<CouponTemplateResponse> {
        requireAdmin(ldap)
        return couponFacade.updateTemplate(couponTemplateId, request.toCommand())
            .let { CouponTemplateResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{couponId}")
    override fun deleteTemplate(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @PathVariable("couponId") couponTemplateId: Long,
    ): ApiResponse<Any> {
        requireAdmin(ldap)
        couponFacade.deleteTemplate(couponTemplateId)
        return ApiResponse.success()
    }

    @GetMapping("/{couponId}/issues")
    override fun findIssuedCouponsByTemplate(
        @Parameter(hidden = true)
        @RequestHeader(name = ADMIN_LDAP_HEADER, required = false)
        ldap: String?,
        @PathVariable("couponId") couponTemplateId: Long,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<IssuedCouponResponse>> {
        requireAdmin(ldap)
        val result = couponFacade.findIssuedCouponsByTemplate(
            templateId = couponTemplateId,
            page = page ?: DEFAULT_PAGE,
            size = size ?: DEFAULT_SIZE,
        )
            .map { IssuedCouponResponse.from(it) }
        return ApiResponse.success(PageResponse.from(result))
    }

    private fun requireAdmin(ldap: String?) {
        if (ldap.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
    }

    companion object {
        private const val ADMIN_LDAP_HEADER = "X-Loopers-Ldap"
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_SIZE = 20
    }
}
