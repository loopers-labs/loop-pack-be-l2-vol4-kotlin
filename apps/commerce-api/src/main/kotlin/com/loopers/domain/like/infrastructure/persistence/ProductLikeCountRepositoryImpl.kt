package com.loopers.domain.like.infrastructure.persistence

import com.loopers.domain.like.port.ProductLikeCountRepository
import org.springframework.stereotype.Component

@Component
class ProductLikeCountRepositoryImpl(
    private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
) : ProductLikeCountRepository {
    override fun create(productId: Long) {
        productLikeCountJpaRepository.save(ProductLikeCountJpaEntity(productId = productId, likeCount = 0))
    }

    override fun increment(productId: Long) {
        productLikeCountJpaRepository.increment(productId)
    }

    override fun decrement(productId: Long) {
        productLikeCountJpaRepository.decrement(productId)
    }

    override fun countByProductId(productId: Long): Long =
        productLikeCountJpaRepository.findById(productId)
            .map { it.likeCount }
            .orElse(0L)

    override fun countByProductIds(productIds: Set<Long>): Map<Long, Long> =
        productLikeCountJpaRepository.findCountsByProductIds(productIds)
            .associate { it.getProductId() to it.getLikeCount() }
}
