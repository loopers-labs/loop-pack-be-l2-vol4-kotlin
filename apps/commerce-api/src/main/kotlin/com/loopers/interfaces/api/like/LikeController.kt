package com.loopers.interfaces.api.like

import com.loopers.domain.auth.AuthService
import com.loopers.domain.common.PageRequest
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.common.PageView
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
    private val likeApplicationService: LikeApplicationServicePort,
    private val authService: AuthService,
) {
    @PostMapping("/products/{productId}/likes")
    fun like(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        val userId = authService.login(loginId, loginPw)
        likeApplicationService.like(userId, productId)
        return ApiResponse.success()
    }

    @DeleteMapping("/products/{productId}/likes")
    fun unlike(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        val userId = authService.login(loginId, loginPw)
        likeApplicationService.unlike(userId, productId)
        return ApiResponse.success()
    }

    @GetMapping("/users/{userId}/likes")
    fun getLikedProducts(
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") loginPw: String,
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageView<LikeV1Dto.LikedProductResponse>> {
        val requesterUserId = authService.login(loginId, loginPw)
        val result = likeApplicationService.getLikedProducts(
            targetUserId = userId,
            requesterUserId = requesterUserId,
            pageRequest = PageRequest(page = page, size = size),
        )
        return ApiResponse.success(PageView.from(result, LikeV1Dto.LikedProductResponse::from))
    }
}
