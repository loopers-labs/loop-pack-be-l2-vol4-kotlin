package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandFacade
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
@RequestMapping("/api-admin/v1/brands")
class AdminBrandV1Controller(
    private val brandFacade: BrandFacade,
) : AdminBrandV1ApiSpec {
    @GetMapping
    override fun getBrands(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<AdminBrandV1Dto.BrandResponse>> {
        LoopersHeaders.validateAdmin(adminId)

        return brandFacade.getBrands(page = page, size = size)
            .map(AdminBrandV1Dto.BrandResponse::from)
            .let { PageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{brandId}")
    override fun getBrand(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable brandId: Long,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return brandFacade.getBrand(brandId)
            .let(AdminBrandV1Dto.BrandResponse::from)
            .let { ApiResponse.success(it) }
    }

    @PostMapping
    override fun createBrand(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @RequestBody request: AdminBrandV1Dto.CreateBrandRequest,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return brandFacade.createBrand(request.toCommand())
            .let(AdminBrandV1Dto.BrandResponse::from)
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/{brandId}")
    override fun updateBrand(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable brandId: Long,
        @RequestBody request: AdminBrandV1Dto.UpdateBrandRequest,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse> {
        LoopersHeaders.validateAdmin(adminId)

        return brandFacade.updateBrand(brandId = brandId, command = request.toCommand())
            .let(AdminBrandV1Dto.BrandResponse::from)
            .let { ApiResponse.success(it) }
    }

    @DeleteMapping("/{brandId}")
    override fun deleteBrand(
        @RequestHeader(LoopersHeaders.ADMIN_LDAP) adminId: String,
        @PathVariable brandId: Long,
    ): ApiResponse<Any> {
        LoopersHeaders.validateAdmin(adminId)

        brandFacade.deleteBrand(brandId)
        return ApiResponse.success()
    }
}
