package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandFacade
import com.loopers.domain.common.PageRequest
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
@RequestMapping("/api-admin/v1/brands")
class BrandAdminController(
    private val brandFacade: BrandFacade,
) {
    @GetMapping
    fun getBrands(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
    ): ApiResponse<BrandAdminV1Dto.BrandsResponse> {
        verifyAdmin(ldap)
        val result = brandFacade.getBrands(PageRequest(page = page, size = size))
        return ApiResponse.success(BrandAdminV1Dto.BrandsResponse.from(result))
    }

    @GetMapping("/{id}")
    fun getBrand(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
    ): ApiResponse<BrandV1Dto.BrandResponse> {
        verifyAdmin(ldap)
        val brand = brandFacade.getBrand(id)
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand))
    }

    @PostMapping
    fun createBrand(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @RequestBody request: BrandAdminV1Dto.CreateBrandRequest,
    ): ApiResponse<BrandV1Dto.BrandResponse> {
        verifyAdmin(ldap)
        val brand = brandFacade.createBrand(request.toCommand())
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand))
    }

    @PutMapping("/{id}")
    fun updateBrand(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
        @RequestBody request: BrandAdminV1Dto.UpdateBrandRequest,
    ): ApiResponse<BrandV1Dto.BrandResponse> {
        verifyAdmin(ldap)
        val brand = brandFacade.updateBrand(request.toCommand(id))
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand))
    }

    @DeleteMapping("/{id}")
    fun deleteBrand(
        @RequestHeader(name = "X-Loopers-Ldap", required = false) ldap: String?,
        @PathVariable id: Long,
    ): ApiResponse<Any> {
        verifyAdmin(ldap)
        brandFacade.deleteBrand(id)
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
