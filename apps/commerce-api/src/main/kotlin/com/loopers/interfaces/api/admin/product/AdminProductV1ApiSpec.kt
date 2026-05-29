package com.loopers.interfaces.api.admin.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Product V1 API", description = "관리자 상품 관리 API 입니다.")
interface AdminProductV1ApiSpec {
    @Operation(
        summary = "관리자 상품 목록 조회",
        description = "관리자가 등록된 상품 목록을 조회합니다.",
    )
    fun getProducts(
        adminId: String,
        brandId: Long?,
        sort: String?,
        page: Int,
        size: Int,
    ): ApiResponse<PageResponse<AdminProductV1Dto.ProductSummaryResponse>>

    @Operation(
        summary = "관리자 상품 상세 조회",
        description = "관리자가 등록된 상품 상세 정보를 조회합니다.",
    )
    fun getProduct(
        adminId: String,
        productId: Long,
    ): ApiResponse<AdminProductV1Dto.ProductDetailResponse>

    @Operation(
        summary = "관리자 상품 등록",
        description = "관리자가 기존 브랜드에 상품을 등록합니다.",
    )
    fun createProduct(
        adminId: String,
        request: AdminProductV1Dto.CreateProductRequest,
    ): ApiResponse<AdminProductV1Dto.ProductDetailResponse>
}
