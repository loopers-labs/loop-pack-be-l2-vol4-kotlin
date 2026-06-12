package com.loopers.domain.like.presentation

import com.loopers.domain.user.application.info.UserInfo
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Like API", description = "Loopers 좋아요 API 입니다.")
interface LikeApiSpec {
    @Operation(
        summary = "상품 좋아요 등록",
        description = "로그인 사용자가 상품에 좋아요를 등록합니다. 반복 호출은 멱등합니다.",
    )
    fun like(
        user: UserInfo,
        productId: Long,
    ): ApiResponse<Any>

    @Operation(
        summary = "상품 좋아요 취소",
        description = "로그인 사용자가 상품 좋아요를 취소합니다. 반복 호출은 멱등합니다.",
    )
    fun unlike(
        user: UserInfo,
        productId: Long,
    ): ApiResponse<Any>
}
