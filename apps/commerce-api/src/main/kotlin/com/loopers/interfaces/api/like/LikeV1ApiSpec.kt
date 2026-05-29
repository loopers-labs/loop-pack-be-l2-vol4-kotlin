package com.loopers.interfaces.api.like

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Like V1 API", description = "Loopers 상품 좋아요 API 입니다.")
interface LikeV1ApiSpec {
    @Operation(summary = "상품 좋아요 등록", description = "인증된 소비자의 상품 좋아요를 멱등하게 등록합니다.")
    fun register(user: User, productId: Long): ApiResponse<LikeV1Dto.LikeResponse>

    @Operation(summary = "상품 좋아요 취소", description = "인증된 소비자의 상품 좋아요를 멱등하게 취소합니다.")
    fun cancel(user: User, productId: Long): ApiResponse<LikeV1Dto.LikeResponse>

    @Operation(summary = "상품 좋아요 현재 상태 조회", description = "인증된 소비자의 상품 좋아요 현재 상태를 반환합니다.")
    fun getCurrentState(user: User, productId: Long): ApiResponse<LikeV1Dto.LikeResponse>
}
