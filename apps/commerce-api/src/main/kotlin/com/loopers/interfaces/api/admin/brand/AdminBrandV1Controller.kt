package com.loopers.interfaces.api.admin.brand

import com.loopers.application.catalog.AdminCatalogFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/brands")
class AdminBrandV1Controller(
    private val adminCatalogFacade: AdminCatalogFacade,
) : AdminBrandV1ApiSpec {
    @PostMapping
    override fun createBrand(
        @RequestHeader("X-Loopers-Ldap") adminId: String,
        @RequestBody request: AdminBrandV1Dto.CreateBrandRequest,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse> {
        return adminCatalogFacade.createBrand(request.toCommand())
            .let(AdminBrandV1Dto.BrandResponse::from)
            .let { ApiResponse.success(it) }
    }
}
