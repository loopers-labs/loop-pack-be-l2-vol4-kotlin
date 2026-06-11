package com.loopers.like.infrastructure

import com.loopers.like.domain.ProductLike
import org.springframework.data.jpa.repository.JpaRepository

interface ProductLikeJpaRepository : JpaRepository<ProductLike, Long> {
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLike?

    fun findByUserIdOrderByIdDesc(userId: Long): List<ProductLike>
}
