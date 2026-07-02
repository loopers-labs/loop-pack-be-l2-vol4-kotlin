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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            insert into product_like_counts (product_id, like_count)
            select p.id, count(l.product_id)
            from products p
            left join likes l on l.product_id = p.id
            group by p.id
            on duplicate key update like_count = values(like_count)
        """,
        nativeQuery = true,
    )
    fun rebuildFromLikes(): Int

    @Query(value = "select count(*) from products", nativeQuery = true)
    fun countProductRows(): Long

    @Query(value = "select count(*) from likes", nativeQuery = true)
    fun countLikeRows(): Long

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
