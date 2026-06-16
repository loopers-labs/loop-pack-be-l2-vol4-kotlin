package com.loopers.like.domain

interface ProductLikeRepository {
    fun save(productLike: ProductLike): ProductLike

    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLike?

    fun delete(productLike: ProductLike)

    fun findAllByUserId(userId: Long): List<ProductLike>
}
