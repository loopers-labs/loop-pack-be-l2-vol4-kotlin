package com.loopers.domain.like.presentation

import com.loopers.domain.like.application.LikeFacade
import com.loopers.domain.user.application.info.UserInfo
import com.loopers.domain.user.presentation.auth.LoginUser
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Validated
class LikeController(
    private val likeFacade: LikeFacade,
) : LikeApiSpec {
    @PostMapping("/products/{productId}/likes")
    override fun like(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @PathVariable("productId") productId: Long,
    ): ApiResponse<Any> {
        likeFacade.like(user.id, productId)
        return ApiResponse.success()
    }

    @DeleteMapping("/products/{productId}/likes")
    override fun unlike(
        @Parameter(hidden = true) @LoginUser user: UserInfo,
        @PathVariable("productId") productId: Long,
    ): ApiResponse<Any> {
        likeFacade.unlike(user.id, productId)
        return ApiResponse.success()
    }
}
