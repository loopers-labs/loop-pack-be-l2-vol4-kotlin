package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.jpa.repository.JpaRepository

interface ProductLikeJpaRepository : JpaRepository<ProductLike, Long> {
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    fun findByUserIdAndProductId(userId: Long, productId: Long): ProductLike?

    fun findByUserId(
        userId: Long,
        scrollPosition: ScrollPosition,
        limit: Limit,
        sort: Sort,
    ): Window<ProductLike>
}
