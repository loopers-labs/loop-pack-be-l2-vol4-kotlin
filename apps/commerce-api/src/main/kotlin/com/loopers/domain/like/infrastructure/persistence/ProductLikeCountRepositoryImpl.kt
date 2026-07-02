package com.loopers.domain.like.infrastructure.persistence

import com.loopers.domain.like.constant.LikeErrorMessages
import com.loopers.domain.like.port.ProductLikeCountRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class ProductLikeCountRepositoryImpl(
    private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
) : ProductLikeCountRepository {
    override fun create(productId: Long) {
        productLikeCountJpaRepository.save(ProductLikeCountJpaEntity(productId = productId, likeCount = 0))
    }

    override fun increment(productId: Long) {
        ensureUpdated(productLikeCountJpaRepository.increment(productId))
    }

    override fun decrement(productId: Long) {
        ensureUpdated(productLikeCountJpaRepository.decrement(productId))
    }

    override fun countByProductId(productId: Long): Long =
        productLikeCountJpaRepository.findById(productId)
            .map { it.likeCount }
            .orElse(0L)

    override fun countByProductIds(productIds: Set<Long>): Map<Long, Long> =
        productLikeCountJpaRepository.findCountsByProductIds(productIds)
            .associate { it.getProductId() to it.getLikeCount() }

    override fun rebuildFromLikes() {
        productLikeCountJpaRepository.rebuildFromLikes()
    }

    override fun countProductRows(): Long =
        productLikeCountJpaRepository.countProductRows()

    override fun countLikeRows(): Long =
        productLikeCountJpaRepository.countLikeRows()

    override fun countProjectionRows(): Long =
        productLikeCountJpaRepository.count()

    private fun ensureUpdated(updatedRows: Int) {
        if (updatedRows != 1) {
            throw CoreException(ErrorType.INTERNAL_ERROR, LikeErrorMessages.LIKE_COUNT_UPDATE_FAILED)
        }
    }
}
