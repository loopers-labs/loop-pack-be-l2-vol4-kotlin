package com.loopers.domain.like.infrastructure.persistence

import com.loopers.domain.like.model.LikeModel
import com.loopers.domain.like.port.LikeRepository
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {
    override fun save(like: LikeModel): Int =
        likeJpaRepository.insertIgnore(like.userId, like.productId)

    override fun delete(
        userId: Long,
        productId: Long,
    ): Int = likeJpaRepository.deleteByUserIdAndProductId(userId, productId)
}
