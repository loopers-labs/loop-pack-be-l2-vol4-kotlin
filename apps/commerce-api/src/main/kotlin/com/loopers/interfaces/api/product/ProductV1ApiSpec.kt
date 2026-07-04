package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Product V1 API", description = "Product 도메인 대고객 API 입니다.")
interface ProductV1ApiSpec {
    @Operation(summary = "상품 목록 조회", description = "카테고리/난이도/브랜드/정렬 필터로 상품 목록을 조회합니다.")
    fun findProducts(
        @Schema(name = "브랜드 ID", description = "특정 브랜드 필터") @RequestParam(required = false) brandId: Long?,
        @Schema(name = "기술 카테고리", description = "BACKEND/FRONTEND/...") @RequestParam(required = false) category: ProductV1Dto.TechCategory?,
        @Schema(name = "난이도", description = "BEGINNER/INTERMEDIATE/ADVANCED") @RequestParam(required = false) level: ProductV1Dto.Level?,
        @Schema(name = "정렬", description = "latest/price_asc/likes_desc") @RequestParam(required = false, defaultValue = "latest") sort: String,
        pageable: Pageable,
    ): ApiResponse<Page<ProductV1Dto.ProductResponse>>

    @Operation(summary = "상품 상세 조회", description = "ID로 상품을 조회합니다.")
    fun getProduct(
        @Schema(name = "상품 ID") @PathVariable productId: Long,
        @Schema(name = "로그인 ID") @RequestHeader(value = "X-Loopers-LoginId", required = false) loginId: String?,
    ): ApiResponse<ProductV1Dto.ProductResponse>
}
