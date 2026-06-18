package com.loopers.domain.like.application.service

import com.loopers.domain.like.exception.InvalidLikeException
import com.loopers.domain.like.model.LikeModel
import com.loopers.domain.like.port.LikeRepository
import com.loopers.domain.like.port.ProductLikeCountRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
    private val productLikeCountRepository: ProductLikeCountRepository,
) {
    @Transactional
    fun like(
        userId: Long,
        productId: Long,
    ): LikeModel {
        val like = createLike(userId, productId)
        val inserted = likeRepository.save(like)
        if (inserted == 1) {
            productLikeCountRepository.increment(productId)
        }
        return like
    }

    @Transactional
    fun unlike(
        userId: Long,
        productId: Long,
    ) {
        createLike(userId, productId)
        val deleted = likeRepository.delete(userId, productId)
        if (deleted == 1) {
            productLikeCountRepository.decrement(productId)
        }
    }

    @Transactional
    fun initializeCount(productId: Long) {
        productLikeCountRepository.create(productId)
    }

    @Transactional(readOnly = true)
    fun countByProductId(productId: Long): Long = productLikeCountRepository.countByProductId(productId)

    @Transactional(readOnly = true)
    fun countByProductIds(productIds: Set<Long>): Map<Long, Long> {
        if (productIds.isEmpty()) {
            return emptyMap()
        }
        return productLikeCountRepository.countByProductIds(productIds)
    }

    private fun createLike(
        userId: Long,
        productId: Long,
    ): LikeModel =
        try {
            LikeModel.of(userId = userId, productId = productId)
        } catch (e: InvalidLikeException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
}
