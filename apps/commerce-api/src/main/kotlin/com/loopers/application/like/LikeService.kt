package com.loopers.application.like

import com.loopers.domain.like.LikeAction
import com.loopers.domain.like.LikeErrorCode
import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeRepository
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductRepository
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
        val product = productRepository.findActiveById(productId)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }
        productLikeRepository.save(ProductLike(userId, productId))
        product.like()
        eventPublisher.publishEvent(LikeChangedEvent(userId, productId, LikeAction.LIKE))
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        val product = productRepository.findActiveById(productId)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val productLike = productLikeRepository.findByUserIdAndProductId(userId, productId)
            ?: return
        productLikeRepository.delete(productLike)
        product.unlike()
        eventPublisher.publishEvent(LikeChangedEvent(userId, productId, LikeAction.UNLIKE))
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

data class LikeChangedEvent(
    val userId: Long,
    val productId: Long,
    val action: LikeAction,
)
