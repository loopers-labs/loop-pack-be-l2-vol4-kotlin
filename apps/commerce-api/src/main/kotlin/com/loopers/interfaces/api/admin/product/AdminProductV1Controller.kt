package com.loopers.interfaces.api.admin.product

import com.loopers.application.admin.product.AdminProductFacade
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.ProductSort
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/products")
class AdminProductV1Controller(
    private val adminProductFacade: AdminProductFacade,
) : AdminProductV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestHeader("X-Loopers-Ldap") adminId: String,
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<AdminProductV1Dto.ProductSummaryResponse>> {
        return adminProductFacade.getProducts(
            ProductListCommand(
                brandId = brandId,
                sort = ProductSort.from(sort),
                page = page,
                size = size,
            ),
        )
            .map(AdminProductV1Dto.ProductSummaryResponse::from)
            .let { PageResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
