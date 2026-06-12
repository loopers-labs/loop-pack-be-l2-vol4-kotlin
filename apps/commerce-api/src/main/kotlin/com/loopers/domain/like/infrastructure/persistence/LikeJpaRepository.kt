package com.loopers.domain.like.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LikeJpaRepository : JpaRepository<LikeJpaEntity, LikeJpaId> {
    @Modifying(clearAutomatically = true)
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

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            delete from likes
            where user_id = :userId and product_id = :productId
        """,
        nativeQuery = true,
    )
    fun deleteByUserIdAndProductId(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
    ): Int
}

interface ProductLikeCountRow {
    fun getProductId(): Long
    fun getLikeCount(): Long
}
