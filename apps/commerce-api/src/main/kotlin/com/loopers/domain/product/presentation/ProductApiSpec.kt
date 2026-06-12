package com.loopers.domain.product.presentation

import com.loopers.domain.product.presentation.response.ProductResponse
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min

@Tag(name = "Product API", description = "Loopers 상품 API 입니다.")
interface ProductApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조건에 맞게 조회합니다.",
    )
    fun findProducts(
        brandId: Long?,
        sort: String?,
        @Min(0)
        page: Int?,
        @Min(1)
        size: Int?,
    ): ApiResponse<List<ProductResponse>>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품 상세를 조회합니다.",
    )
    fun getProduct(productId: Long): ApiResponse<ProductResponse>
}
