package com.loopers.application.like

import com.loopers.domain.event.ProductLikedEvent
import com.loopers.domain.event.ProductUnlikedEvent
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.ProductService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productService: ProductService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun like(userId: Long, productId: Long) {
        productService.getProduct(productId)
        val created = likeService.like(userId, productId)
        if (created) {
            eventPublisher.publishEvent(ProductLikedEvent(userId = userId, productId = productId))
        }
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        val deleted = likeService.unlike(userId, productId)
        if (deleted) {
            eventPublisher.publishEvent(ProductUnlikedEvent(userId = userId, productId = productId))
        }
    }

    fun getMyLikes(userId: Long, pageable: Pageable): Page<LikeInfo> {
        val likes = likeService.getLikesByUserId(userId, pageable)
        val productIds = likes.content.map { it.productId }.distinct()
        val products = productService.getProductsByIds(productIds)

        return likes.map { like -> LikeInfo.of(like, products[like.productId]) }
    }
}
