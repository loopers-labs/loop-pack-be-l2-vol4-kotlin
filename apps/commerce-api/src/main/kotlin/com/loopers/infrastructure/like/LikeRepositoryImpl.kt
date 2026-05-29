package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.Like
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
    private val likeCommandRepository: LikeCommandRepository,
) : LikeRepository {
    override fun findByUserIdAndProductId(userId: Long, productId: Long): Like? {
        return likeJpaRepository.findByUserIdAndProductId(userId = userId, productId = productId)
            ?.toDomain()
    }

    override fun createIfAbsent(like: Like): Boolean {
        return likeCommandRepository.createIfAbsent(userId = like.userId, productId = like.productId) == 1
    }

    override fun restoreIfCanceled(userId: Long, productId: Long): Boolean {
        return likeCommandRepository.restoreIfCanceled(userId = userId, productId = productId) == 1
    }

    override fun cancelIfActive(userId: Long, productId: Long): Boolean {
        return likeCommandRepository.cancelIfActive(userId = userId, productId = productId) == 1
    }
}
