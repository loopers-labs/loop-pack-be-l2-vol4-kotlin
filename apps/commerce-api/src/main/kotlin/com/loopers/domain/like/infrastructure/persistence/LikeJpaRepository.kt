package com.loopers.domain.like.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LikeJpaRepository : JpaRepository<LikeJpaEntity, LikeJpaId> {
    fun existsByIdUserIdAndIdProductId(
        userId: Long,
        productId: Long,
    ): Boolean

    fun countByIdProductId(productId: Long): Long

    @Query(
        """
        select l.id.productId as productId, count(l) as likeCount
        from LikeJpaEntity l
        where l.id.productId in :productIds
        group by l.id.productId
        """,
    )
    fun countByProductIds(
        @Param("productIds") productIds: Set<Long>,
    ): List<ProductLikeCountRow>

    @Modifying
    @Query(
        value = """
            insert ignore into likes (user_id, product_id, created_at)
            values (:userId, :productId, current_timestamp)
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
    ): Int
}

interface ProductLikeCountRow {
    fun getProductId(): Long
    fun getLikeCount(): Long
}
