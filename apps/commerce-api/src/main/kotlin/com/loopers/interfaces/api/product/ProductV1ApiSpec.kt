package com.loopers.interfaces.api.product

import com.loopers.domain.product.ProductSortType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "Product V1 API", description = "상품 API")
interface ProductV1ApiSpec {
    @Operation(summary = "상품 생성")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(
        @Valid @RequestBody request: ProductV1Dto.CreateRequest,
    ): ApiResponse<ProductV1Dto.ProductResponse>

    @Operation(summary = "상품 상세 조회")
    fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductResponse>

    @Operation(summary = "상품 목록 조회")
    fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(defaultValue = "LATEST") sort: ProductSortType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>

    @Operation(summary = "상품 수정")
    fun updateProduct(
        @PathVariable productId: Long,
        @Valid @RequestBody request: ProductV1Dto.UpdateRequest,
    ): ApiResponse<ProductV1Dto.ProductResponse>

    @Operation(summary = "상품 삭제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(
        @PathVariable productId: Long,
    ): ApiResponse<Any>
}
