package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeCountModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.ZonedDateTime

interface ProductLikeCountJpaRepository : JpaRepository<ProductLikeCountModel, Long> {
    fun findByProductIdIn(productIds: List<Long>): List<ProductLikeCountModel>

    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO product_like_count (product_id, like_count, refreshed_at)
        VALUES (:productId, GREATEST(:delta, 0), :now) 
        ON DUPLICATE KEY UPDATE like_count = GREATEST(0, like_count + :delta), refreshed_at = :now
    """, nativeQuery = true)
    fun upsertLikeCount(productId: Long, delta: Int, now: ZonedDateTime): Int
}
