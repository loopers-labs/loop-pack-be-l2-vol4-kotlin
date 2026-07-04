package com.loopers.application.like

import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeResult
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.ProductId
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val productService: ProductService,
    private val userService: UserService,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun addLike(loginId: String, productId: Long): Boolean {
        val user = userService.getByLoginId(loginId)
        val product = productService.getProduct(productId)

        val result = likeService.addLike(user.id, product.id)
        if (result is LikeResult.Liked) {
            eventPublisher.publishEvent(LikeEvent.Changed(UUID.randomUUID().toString(), user.id, product.id,
                LikeEvent.LikeChangeType.LIKED, ZonedDateTime.now())
            )
        }

        return result is LikeResult.Liked
    }

    @Transactional
    fun removeLike(loginId: String, productId: Long): Unit {
        val user = userService.getByLoginId(loginId)
        val product = productService.getProduct(productId)

        val removed = likeService.remove(user.id, product.id)
        if (removed) {
            eventPublisher.publishEvent(LikeEvent.Changed(UUID.randomUUID().toString(), user.id, product.id,
                LikeEvent.LikeChangeType.UNLIKED, ZonedDateTime.now())
            )
        }
    }

    fun findLikes(requestLoginId: String, pathUserId: Long): List<LikedProductInfo> {
        val user = userService.getByLoginId(requestLoginId)
        user.validateSelf(pathUserId)

        val likes = likeService.getLikeByUserId(user.id).filter { it.available() }
        val productIds = likes.map { like -> like.productId }
        val likeCounts = likeService.getLikeCountGroupByProductId(productIds)

        val products = productService.getProducts(productIds)
            .associateBy { it.id }

        return likes.map {
            val product = products.getValue(it.productId)
            val likeCount = likeCounts[ProductId(product.id)]
            LikedProductInfo.of(it, product, likeCount)
        }
    }
}
