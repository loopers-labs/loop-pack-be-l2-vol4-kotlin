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
) : LikeV1ApiSpec {
    @PostMapping
    override fun like(
        @RequestHeader("X-Loopers-User-Id") memberId: Long,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.like(memberId = memberId, productId = productId)
        return ApiResponse.success()
    }

    @DeleteMapping
    override fun unlike(
        @RequestHeader("X-Loopers-User-Id") memberId: Long,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.unlike(memberId = memberId, productId = productId)
        return ApiResponse.success()
    }
}
