package com.loopers.like.application

import com.loopers.product.domain.event.ProductLikedEvent
import com.loopers.product.domain.event.ProductUnlikedEvent
import com.loopers.like.domain.LikeErrorCode
import com.loopers.like.domain.ProductLike
import com.loopers.like.domain.ProductLikeRepository
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductRepository
import com.loopers.support.error.ForbiddenException
import com.loopers.support.error.NotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val productRepository: ProductRepository,
    private val productLikeRepository: ProductLikeRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // TODO(동시성): existsBy 선검사 TOCTOU는 비범위(R1) — 추후 UK 위반 변환/락으로 보강.
    @Transactional
    fun like(userId: Long, productId: Long) {
        productRepository.findActiveById(productId)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }
        productLikeRepository.save(ProductLike(userId, productId))
        eventPublisher.publishEvent(ProductLikedEvent(userId, productId))
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        productRepository.findActiveById(productId)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val productLike = productLikeRepository.findByUserIdAndProductId(userId, productId)
            ?: return
        productLikeRepository.delete(productLike)
        eventPublisher.publishEvent(ProductUnlikedEvent(userId, productId))
    }

    // read 단일 쿼리라 @Transactional 미부착 (docs/week3/06, Brand 결정과 동일).
    fun findMine(requesterId: Long, userId: Long): List<LikeInfo> {
        if (requesterId != userId) {
            throw ForbiddenException(LikeErrorCode.FORBIDDEN_LIKE_ACCESS)
        }
        return productLikeRepository.findAllByUserId(userId).map(LikeInfo::from)
    }
}

data class LikeInfo(
    val productId: Long,
) {
    companion object {
        fun from(productLike: ProductLike): LikeInfo =
            LikeInfo(productId = productLike.productId)
    }
}
