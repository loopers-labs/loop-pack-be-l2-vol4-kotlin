package com.loopers.domain.like

import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun like(userId: Long, productId: Long) {
        validateUserAndProduct(userId = userId, productId = productId)
        if (likeRepository.findByUserIdAndProductId(userId = userId, productId = productId) != null) {
            return
        }

        likeRepository.save(LikeModel(userId = userId, productId = productId))
        eventPublisher.publishEvent(LikeCreatedEvent(productId = productId))
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        validateUserAndProduct(userId = userId, productId = productId)
        val like = likeRepository.findByUserIdAndProductId(userId = userId, productId = productId) ?: return

        likeRepository.delete(like)
        eventPublisher.publishEvent(LikeDeletedEvent(productId = productId))
    }

    private fun validateUserAndProduct(userId: Long, productId: Long) {
        userRepository.findById(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다.")
        if (!productRepository.existsActiveById(productId)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        }
    }
}
