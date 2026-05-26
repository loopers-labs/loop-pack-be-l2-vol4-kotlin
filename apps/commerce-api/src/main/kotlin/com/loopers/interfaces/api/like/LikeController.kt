package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.domain.auth.AuthService
import com.loopers.domain.common.PageRequest
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class LikeController(
    private val likeFacade: LikeFacade,
    private val authService: AuthService,
) {
    @PostMapping("/products/{productId}/likes")
    fun like(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        val userId = authService.login(loginId, loginPw)
        likeFacade.like(userId, productId)
        return ApiResponse.success()
    }

    @DeleteMapping("/products/{productId}/likes")
    fun unlike(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        val userId = authService.login(loginId, loginPw)
        likeFacade.unlike(userId, productId)
        return ApiResponse.success()
    }

    @GetMapping("/users/{userId}/likes")
    fun getLikedProducts(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<LikeV1Dto.LikedProductsResponse> {
        val requesterUserId = authService.login(loginId, loginPw)
        val result = likeFacade.getLikedProducts(
            targetUserId = userId,
            requesterUserId = requesterUserId,
            pageRequest = PageRequest(page = page, size = size),
        )
        return ApiResponse.success(LikeV1Dto.LikedProductsResponse.from(result))
    }
}
