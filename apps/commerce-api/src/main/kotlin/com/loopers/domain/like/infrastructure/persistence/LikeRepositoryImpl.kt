package com.loopers.domain.like.infrastructure.persistence

import com.loopers.domain.like.model.LikeModel
import com.loopers.domain.like.port.LikeRepository
import org.springframework.stereotype.Component

@Component
class LikeRepositoryImpl(
    private val likeJpaRepository: LikeJpaRepository,
) : LikeRepository {
    override fun exists(
        userId: Long,
        productId: Long,
    ): Boolean = likeJpaRepository.existsByIdUserIdAndIdProductId(userId, productId)

    override fun save(like: LikeModel): LikeModel {
        likeJpaRepository.insertIgnore(like.userId, like.productId)
        return like
    }

    override fun delete(
        userId: Long,
        productId: Long,
    ) {
        val id = LikeJpaId(userId = userId, productId = productId)
        if (likeJpaRepository.existsById(id)) {
            likeJpaRepository.deleteById(id)
        }
    }

    override fun countByProductId(productId: Long): Long =
        likeJpaRepository.countByIdProductId(productId)

    override fun countByProductIds(productIds: Set<Long>): Map<Long, Long> =
        likeJpaRepository.countByProductIds(productIds)
            .associate { it.getProductId() to it.getLikeCount() }
}
