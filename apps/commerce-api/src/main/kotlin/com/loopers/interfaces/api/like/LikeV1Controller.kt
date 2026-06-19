package com.loopers.interfaces.api.like

import com.loopers.application.like.ProductLikeFacade
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
class LikeV1Controller(
    private val productLikeFacade: ProductLikeFacade,
) : LikeV1ApiSpec {
    @LoginRequired
    @PostMapping
    override fun register(
        @CurrentUser user: User,
        @PathVariable productId: Long,
    ): ApiResponse<LikeV1Dto.LikeResponse> =
        productLikeFacade.register(user.id, productId)
            .let(LikeV1Dto.LikeResponse::from)
            .let(ApiResponse.Companion::success)

    @LoginRequired
    @DeleteMapping
    override fun cancel(
        @CurrentUser user: User,
        @PathVariable productId: Long,
    ): ApiResponse<LikeV1Dto.LikeResponse> =
        productLikeFacade.cancel(user.id, productId)
            .let(LikeV1Dto.LikeResponse::from)
            .let(ApiResponse.Companion::success)

    @LoginRequired
    @GetMapping("/me")
    override fun getCurrentState(
        @CurrentUser user: User,
        @PathVariable productId: Long,
    ): ApiResponse<LikeV1Dto.LikeResponse> =
        productLikeFacade.isLiked(user.id, productId)
            .let(LikeV1Dto.LikeResponse::from)
            .let(ApiResponse.Companion::success)
}
