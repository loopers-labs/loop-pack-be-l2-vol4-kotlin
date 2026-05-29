package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Like V1 API", description = "상품 좋아요 API 입니다.")
interface LikeV1ApiSpec {
    @Operation(
        summary = "상품 좋아요 등록",
        description = "상품을 좋아요 상태로 만듭니다. 이미 좋아요 상태여도 성공합니다.",
    )
    fun like(
        memberId: Long,
        productId: Long,
    ): ApiResponse<Any>

    @Operation(
        summary = "상품 좋아요 취소",
        description = "상품을 좋아요하지 않은 상태로 만듭니다. 이미 좋아요하지 않은 상태여도 성공합니다.",
    )
    fun unlike(
        memberId: Long,
        productId: Long,
    ): ApiResponse<Any>
}
