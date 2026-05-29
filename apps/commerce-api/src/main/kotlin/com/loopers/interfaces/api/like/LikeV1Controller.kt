package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class LikeV1Controller(
    private val likeFacade: LikeFacade,
) : LikeV1ApiSpec {
    @PostMapping("/products/{productId}/likes")
    override fun like(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.like(loginId = loginId, rawPassword = password, productId = productId)
        return ApiResponse.success()
    }

    @DeleteMapping("/products/{productId}/likes")
    override fun unlike(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.unlike(loginId = loginId, rawPassword = password, productId = productId)
        return ApiResponse.success()
    }

    @GetMapping("/users/{userId}/likes")
    override fun getLikedProducts(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
        @PathVariable userId: Long,
    ): ApiResponse<List<LikeV1Dto.LikedProductResponse>> {
        val likedProducts = likeFacade.getLikedProducts(
            loginId = loginId,
            rawPassword = password,
            userId = userId,
        )

        return likedProducts
            .map(LikeV1Dto.LikedProductResponse::from)
            .let { ApiResponse.success(it) }
    }
}
