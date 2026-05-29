package com.loopers.interfaces.api.catalog

import com.loopers.application.catalog.CatalogApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.Admin
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Admin
@RestController
@RequestMapping("/api/v1/admin")
class AdminCatalogV1Controller(
    private val catalogApplicationService: CatalogApplicationService,
) : AdminCatalogV1ApiSpec {
    @PostMapping("/brands")
    override fun createBrand(
        @RequestBody @Valid request: CatalogV1Dto.CreateBrandRequest,
    ): ApiResponse<CatalogV1Dto.BrandResponse> =
        catalogApplicationService.createBrand(request.toCommand())
            .let(CatalogV1Dto.BrandResponse::from)
            .let(ApiResponse.Companion::success)

    @PatchMapping("/brands/{brandId}")
    override fun updateBrand(
        @PathVariable brandId: Long,
        @RequestBody @Valid request: CatalogV1Dto.UpdateBrandRequest,
    ): ApiResponse<CatalogV1Dto.BrandResponse> =
        catalogApplicationService.updateBrand(brandId, request.toCommand())
            .let(CatalogV1Dto.BrandResponse::from)
            .let(ApiResponse.Companion::success)

    @PatchMapping("/brands/{brandId}/activate")
    override fun activateBrand(@PathVariable brandId: Long): ApiResponse<CatalogV1Dto.BrandResponse> =
        catalogApplicationService.activateBrand(brandId)
            .let(CatalogV1Dto.BrandResponse::from)
            .let(ApiResponse.Companion::success)

    @PatchMapping("/brands/{brandId}/deactivate")
    override fun deactivateBrand(@PathVariable brandId: Long): ApiResponse<CatalogV1Dto.BrandResponse> =
        catalogApplicationService.deactivateBrand(brandId)
            .let(CatalogV1Dto.BrandResponse::from)
            .let(ApiResponse.Companion::success)

    @DeleteMapping("/brands/{brandId}")
    override fun deleteBrand(@PathVariable brandId: Long): ApiResponse<Unit> {
        catalogApplicationService.deleteBrand(brandId)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/products")
    override fun createProduct(
        @RequestBody @Valid request: CatalogV1Dto.CreateProductRequest,
    ): ApiResponse<CatalogV1Dto.ProductResponse> =
        catalogApplicationService.createProduct(request.toCommand())
            .let(CatalogV1Dto.ProductResponse::from)
            .let(ApiResponse.Companion::success)

    @PatchMapping("/products/{productId}")
    override fun updateProduct(
        @PathVariable productId: Long,
        @RequestBody @Valid request: CatalogV1Dto.UpdateProductRequest,
    ): ApiResponse<CatalogV1Dto.ProductResponse> =
        catalogApplicationService.updateProduct(productId, request.toCommand())
            .let(CatalogV1Dto.ProductResponse::from)
            .let(ApiResponse.Companion::success)

    @PatchMapping("/products/{productId}/activate")
    override fun activateProduct(@PathVariable productId: Long): ApiResponse<CatalogV1Dto.ProductResponse> =
        catalogApplicationService.activateProduct(productId)
            .let(CatalogV1Dto.ProductResponse::from)
            .let(ApiResponse.Companion::success)

    @PatchMapping("/products/{productId}/suspend")
    override fun suspendProduct(@PathVariable productId: Long): ApiResponse<CatalogV1Dto.ProductResponse> =
        catalogApplicationService.suspendProduct(productId)
            .let(CatalogV1Dto.ProductResponse::from)
            .let(ApiResponse.Companion::success)

    @DeleteMapping("/products/{productId}")
    override fun deleteProduct(@PathVariable productId: Long): ApiResponse<Unit> {
        catalogApplicationService.deleteProduct(productId)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/products/{productId}/stocks/add")
    override fun addStock(
        @PathVariable productId: Long,
        @RequestBody @Valid request: CatalogV1Dto.ChangeStockRequest,
    ): ApiResponse<Unit> {
        catalogApplicationService.addStock(request.toCommand(productId))
        return ApiResponse.success(Unit)
    }
}
