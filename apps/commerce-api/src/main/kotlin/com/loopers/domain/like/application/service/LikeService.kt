package com.loopers.domain.like.application.service

import com.loopers.domain.like.exception.InvalidLikeException
import com.loopers.domain.like.model.LikeModel
import com.loopers.domain.like.port.LikeRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeService(
    private val likeRepository: LikeRepository,
) {
    @Transactional
    fun like(
        userId: Long,
        productId: Long,
    ): LikeModel =
        try {
            val like = LikeModel.of(userId = userId, productId = productId)
            if (likeRepository.exists(userId, productId)) {
                like
            } else {
                likeRepository.save(like)
            }
        } catch (e: InvalidLikeException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }

    @Transactional
    fun unlike(
        userId: Long,
        productId: Long,
    ) {
        try {
            LikeModel.of(userId = userId, productId = productId)
        } catch (e: InvalidLikeException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
        likeRepository.delete(userId, productId)
    }

    @Transactional(readOnly = true)
    fun countByProductId(productId: Long): Long = likeRepository.countByProductId(productId)

    @Transactional(readOnly = true)
    fun countByProductIds(productIds: Set<Long>): Map<Long, Long> {
        if (productIds.isEmpty()) {
            return emptyMap()
        }
        return likeRepository.countByProductIds(productIds)
    }
}
