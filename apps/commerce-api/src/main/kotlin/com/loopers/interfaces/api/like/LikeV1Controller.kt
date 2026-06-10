package com.loopers.interfaces.api.like

import com.loopers.application.like.usecase.LikeProductCommand
import com.loopers.application.like.usecase.LikeProductUsecase
import com.loopers.application.like.usecase.UnlikeProductUsecase
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
class LikeV1Controller(
    private val likeProductUsecase: LikeProductUsecase,
    private val unlikeProductUsecase: UnlikeProductUsecase,
) {
    @PostMapping
    fun like(
        @PathVariable productId: Long,
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<Any> {
        likeProductUsecase.execute(LikeProductCommand(loginId = loginId, password = password, productId = productId))
        return ApiResponse.success()
    }

    @DeleteMapping
    fun unlike(
        @PathVariable productId: Long,
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<Any> {
        unlikeProductUsecase.execute(LikeProductCommand(loginId = loginId, password = password, productId = productId))
        return ApiResponse.success()
    }
}
