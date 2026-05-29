package com.loopers.interfaces.api.catalog

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Catalog V1 API", description = "Loopers admin catalog API.")
interface AdminCatalogV1ApiSpec {
    @Operation(summary = "브랜드 생성", description = "신규 브랜드를 생성합니다.")
    fun createBrand(request: CatalogV1Dto.CreateBrandRequest): ApiResponse<CatalogV1Dto.BrandResponse>

    @Operation(summary = "브랜드 수정", description = "브랜드 이름을 수정합니다.")
    fun updateBrand(brandId: Long, request: CatalogV1Dto.UpdateBrandRequest): ApiResponse<CatalogV1Dto.BrandResponse>

    @Operation(summary = "브랜드 활성화", description = "브랜드 상태를 ACTIVE 로 변경합니다.")
    fun activateBrand(brandId: Long): ApiResponse<CatalogV1Dto.BrandResponse>

    @Operation(summary = "브랜드 비활성화", description = "브랜드 상태를 INACTIVE 로 변경합니다.")
    fun deactivateBrand(brandId: Long): ApiResponse<CatalogV1Dto.BrandResponse>

    @Operation(summary = "브랜드 삭제", description = "브랜드를 soft delete 합니다.")
    fun deleteBrand(brandId: Long): ApiResponse<Unit>

    @Operation(summary = "상품 생성", description = "신규 상품과 재고, 통계, 상세 이미지를 생성합니다.")
    fun createProduct(request: CatalogV1Dto.CreateProductRequest): ApiResponse<CatalogV1Dto.ProductResponse>

    @Operation(summary = "상품 수정", description = "상품 정보와 상세 이미지를 수정합니다.")
    fun updateProduct(productId: Long, request: CatalogV1Dto.UpdateProductRequest): ApiResponse<CatalogV1Dto.ProductResponse>

    @Operation(summary = "상품 활성화", description = "상품 상태를 ON_SALE 로 변경합니다.")
    fun activateProduct(productId: Long): ApiResponse<CatalogV1Dto.ProductResponse>

    @Operation(summary = "상품 판매중지", description = "상품 상태를 SUSPENDED 로 변경합니다.")
    fun suspendProduct(productId: Long): ApiResponse<CatalogV1Dto.ProductResponse>

    @Operation(summary = "상품 삭제", description = "상품을 soft delete 합니다.")
    fun deleteProduct(productId: Long): ApiResponse<Unit>

    @Operation(summary = "재고 추가", description = "상품의 실제 재고를 추가합니다.")
    fun addStock(productId: Long, request: CatalogV1Dto.ChangeStockRequest): ApiResponse<Unit>
}
