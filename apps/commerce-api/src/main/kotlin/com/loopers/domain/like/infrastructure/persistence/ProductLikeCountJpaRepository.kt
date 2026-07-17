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
            insert into product_metrics (
                product_id,
                like_count,
                sales_count,
                view_count,
                last_event_at,
                last_like_event_at,
                last_sales_event_at,
                last_view_event_at,
                updated_at
            )
            select p.id, count(l.product_id), 0, 0, null, null, null, null, current_timestamp
            from products p
            left join likes l on l.product_id = p.id
            group by p.id
            on duplicate key update
                like_count = values(like_count),
                updated_at = current_timestamp
        """,
        nativeQuery = true,
    )
    fun rebuildFromLikes(): Int

    @Query(value = "select count(*) from products", nativeQuery = true)
    fun countProductRows(): Long

    @Query(value = "select count(*) from likes", nativeQuery = true)
    fun countLikeRows(): Long
}
