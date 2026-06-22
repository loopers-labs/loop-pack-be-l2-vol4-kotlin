package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.like.dto.LikeV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Like V1 API", description = "상품 좋아요 API 입니다.")
interface LikeV1ApiSpec {
    @Operation(
        summary = "상품 좋아요 등록",
        description = "상품을 좋아요 상태로 만듭니다. 이미 좋아요 상태여도 성공합니다.",
    )
    fun like(
        loginId: String,
        password: String,
        productId: Long,
    ): ApiResponse<Any>

    @Operation(
        summary = "상품 좋아요 취소",
        description = "상품을 좋아요하지 않은 상태로 만듭니다. 이미 좋아요하지 않은 상태여도 성공합니다.",
    )
    fun unlike(
        loginId: String,
        password: String,
        productId: Long,
    ): ApiResponse<Any>

    @Operation(
        summary = "내가 좋아요한 상품 목록 조회",
        description = "로그인한 회원이 좋아요한 상품 목록을 조회합니다.",
    )
    fun getLikedProducts(
        loginId: String,
        password: String,
        userId: Long,
    ): ApiResponse<List<LikeV1Dto.LikedProductResponse>>
}
