package com.loopers.projection.like.infrastructure.persistence

import com.loopers.projection.like.port.ProductLikeCountProjectionRepository
import org.springframework.stereotype.Component

@Component
class ProductLikeCountProjectionRepositoryImpl(
    private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
) : ProductLikeCountProjectionRepository {
    override fun increment(productId: Long): Int =
        productLikeCountJpaRepository.increment(productId)

    override fun decrement(productId: Long): Int =
        productLikeCountJpaRepository.decrement(productId)
}
