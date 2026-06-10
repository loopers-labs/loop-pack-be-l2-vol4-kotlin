package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeModel
import com.loopers.domain.like.LikeRepository
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {
    override fun save(like: LikeModel): LikeModel {
        return likeJpaRepository.save(like)
    }

    override fun findByUserIdAndProductId(userId: Long, productId: Long): LikeModel? {
        return likeJpaRepository.findByUserIdAndProductId(userId = userId, productId = productId)
    }

    override fun delete(like: LikeModel) {
        likeJpaRepository.delete(like)
    }

    override fun countByProductId(productId: Long): Long {
        return likeJpaRepository.countByProductId(productId)
    }
}
