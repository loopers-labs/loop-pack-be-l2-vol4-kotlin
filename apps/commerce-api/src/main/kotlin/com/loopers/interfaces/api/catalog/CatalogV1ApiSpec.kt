package com.loopers.interfaces.api.catalog

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Catalog V1 API", description = "Loopers catalog API.")
interface CatalogV1ApiSpec {
    @Operation(summary = "상품 목록 조회", description = "전시 가능한 상품 목록을 조회합니다.")
    fun getProducts(sort: String, page: Int, size: Int): ApiResponse<List<CatalogV1Dto.ProductDisplayResponse>>

    @Operation(summary = "상품 상세 조회", description = "전시 가능한 상품 상세를 조회합니다.")
    fun getProductDetail(productId: Long): ApiResponse<CatalogV1Dto.ProductDetailResponse>
}
