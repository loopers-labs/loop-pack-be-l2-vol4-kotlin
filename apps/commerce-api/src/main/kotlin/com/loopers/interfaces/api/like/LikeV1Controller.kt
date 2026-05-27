package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
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
    private val likeFacade: LikeFacade,
) {
    @PostMapping
    fun like(
        @PathVariable productId: Long,
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<Any> {
        likeFacade.like(LikeFacade.LikeCommand(loginId = loginId, password = password, productId = productId))
        return ApiResponse.success()
    }

    @DeleteMapping
    fun unlike(
        @PathVariable productId: Long,
        @RequestHeader("X-Loopers-LoginId") loginId: String,
        @RequestHeader("X-Loopers-LoginPw") password: String,
    ): ApiResponse<Any> {
        likeFacade.unlike(LikeFacade.LikeCommand(loginId = loginId, password = password, productId = productId))
        return ApiResponse.success()
    }
}
