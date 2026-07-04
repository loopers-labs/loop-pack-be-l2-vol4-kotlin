package com.loopers.domain.like.application

import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.product.application.service.ProductService
import org.springframework.stereotype.Component

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productService: ProductService,
) {
    fun like(
        userId: Long,
        productId: Long,
    ) {
        productService.getById(productId)
        likeService.like(userId, productId)
    }

    fun unlike(
        userId: Long,
        productId: Long,
    ) {
        productService.getById(productId)
        likeService.unlike(userId, productId)
    }
}
