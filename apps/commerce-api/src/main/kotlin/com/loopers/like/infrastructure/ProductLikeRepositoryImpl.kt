package com.loopers.like.infrastructure

import com.loopers.like.domain.ProductLike
import com.loopers.like.domain.ProductLikeRepository
import org.springframework.stereotype.Repository

@Repository
class ProductLikeRepositoryImpl(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) : ProductLikeRepository {
    override fun save(productLike: ProductLike): ProductLike =
        productLikeJpaRepository.save(productLike)

    override fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean =
        productLikeJpaRepository.existsByUserIdAndProductId(userId, productId)

    override fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLike? =
        productLikeJpaRepository.findByUserIdAndProductId(userId, productId)

    override fun delete(productLike: ProductLike) {
        productLikeJpaRepository.delete(productLike)
    }

    override fun findAllByUserId(userId: Long): List<ProductLike> =
        productLikeJpaRepository.findByUserIdOrderByIdDesc(userId)
}
