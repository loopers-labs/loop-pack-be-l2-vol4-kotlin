package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeCursor
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductLikeCursorJpaRepository : JpaRepository<ProductLikeCursor, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            insert ignore into product_like_cursors
                (user_id, product_id, last_history_id, created_at, updated_at)
            values
                (:userId, :productId, null, current_timestamp(6), current_timestamp(6))
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
    ): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select cursor from ProductLikeCursor cursor where cursor.userId = :userId and cursor.productId = :productId",
    )
    fun findByUserIdAndProductIdForUpdate(
        @Param("userId") userId: Long,
        @Param("productId") productId: Long,
    ): ProductLikeCursor?
}
