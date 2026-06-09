package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Product V1 API", description = "상품 조회 API 입니다.")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "브랜드 필터와 정렬 조건으로 고객 상품 목록을 조회합니다.",
    )
    fun getProducts(
        brandId: Long?,
        sort: String?,
        page: Int,
        size: Int,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 브랜드, 재고, 좋아요 수를 포함한 상품 상세를 조회합니다.",
    )
    fun getProduct(productId: Long): ApiResponse<ProductV1Dto.ProductDetailResponse>
}
