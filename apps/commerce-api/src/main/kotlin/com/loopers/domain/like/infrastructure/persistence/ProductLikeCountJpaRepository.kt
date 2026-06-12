package com.loopers.domain.like.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductLikeCountJpaRepository : JpaRepository<ProductLikeCountJpaEntity, Long> {
    @Query(
        """
        select c.productId as productId, c.likeCount as likeCount
        from ProductLikeCountJpaEntity c
        where c.productId in :productIds
        """,
    )
    fun findCountsByProductIds(
        @Param("productIds") productIds: Set<Long>,
    ): List<ProductLikeCountRow>

    @Modifying(clearAutomatically = true)
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

    @Modifying(clearAutomatically = true)
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
