package com.loopers.interfaces.api.admin.brand

import com.loopers.application.admin.brand.AdminBrandFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
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
class AdminBrandV1Controller(
    private val adminBrandFacade: AdminBrandFacade,
) : AdminBrandV1ApiSpec {
    @GetMapping
    override fun getBrands(
        @RequestHeader("X-Loopers-Ldap") adminId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<AdminBrandV1Dto.BrandResponse>> {
        return adminBrandFacade.getBrands(page = page, size = size)
            .map(AdminBrandV1Dto.BrandResponse::from)
            .let { PageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{brandId}")
    override fun getBrand(
        @RequestHeader("X-Loopers-Ldap") adminId: String,
        @PathVariable brandId: Long,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse> {
        return adminBrandFacade.getBrand(brandId)
            .let(AdminBrandV1Dto.BrandResponse::from)
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    override fun createBrand(
        @RequestHeader("X-Loopers-Ldap") adminId: String,
        @RequestBody request: AdminBrandV1Dto.CreateBrandRequest,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse> {
        return adminBrandFacade.createBrand(request.toCommand())
            .let(AdminBrandV1Dto.BrandResponse::from)
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{brandId}")
    override fun updateBrand(
        @RequestHeader("X-Loopers-Ldap") adminId: String,
        @PathVariable brandId: Long,
        @RequestBody request: AdminBrandV1Dto.UpdateBrandRequest,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse> {
        return adminBrandFacade.updateBrand(brandId = brandId, command = request.toCommand())
            .let(AdminBrandV1Dto.BrandResponse::from)
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{brandId}")
    override fun deleteBrand(
        @RequestHeader("X-Loopers-Ldap") adminId: String,
        @PathVariable brandId: Long,
    ): ApiResponse<Any> {
        adminBrandFacade.deleteBrand(brandId)
        return ApiResponse.success()
    }
}
