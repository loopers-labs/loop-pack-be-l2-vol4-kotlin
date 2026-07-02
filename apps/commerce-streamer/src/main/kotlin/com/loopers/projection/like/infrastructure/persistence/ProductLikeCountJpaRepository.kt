package com.loopers.projection.like.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductLikeCountJpaRepository : JpaRepository<ProductLikeCountJpaEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            update product_like_counts
            set like_count = like_count + 1
            where product_id = :productId
        """,
        nativeQuery = true,
    )
    fun increment(
        @Param("productId") productId: Long,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            update product_like_counts
            set like_count = like_count - 1
            where product_id = :productId and like_count > 0
        """,
        nativeQuery = true,
    )
    fun decrement(
        @Param("productId") productId: Long,
    ): Int
}
