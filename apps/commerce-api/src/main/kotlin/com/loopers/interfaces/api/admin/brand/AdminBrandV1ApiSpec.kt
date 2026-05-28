package com.loopers.interfaces.api.admin.brand

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Brand V1 API", description = "관리자 브랜드 관리 API 입니다.")
interface AdminBrandV1ApiSpec {
    @Operation(
        summary = "관리자 브랜드 목록 조회",
        description = "관리자가 등록된 브랜드 목록을 조회합니다.",
    )
    fun getBrands(
        adminId: String,
        page: Int,
        size: Int,
    ): ApiResponse<PageResponse<AdminBrandV1Dto.BrandResponse>>

    @Operation(
        summary = "관리자 브랜드 등록",
        description = "관리자가 브랜드를 등록합니다.",
    )
    fun createBrand(
        adminId: String,
        request: AdminBrandV1Dto.CreateBrandRequest,
    ): ApiResponse<AdminBrandV1Dto.BrandResponse>
}
