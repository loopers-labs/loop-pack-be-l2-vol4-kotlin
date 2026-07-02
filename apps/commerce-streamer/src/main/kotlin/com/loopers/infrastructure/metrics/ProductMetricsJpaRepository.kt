package com.loopers.infrastructure.metrics

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductMetricsJpaRepository : JpaRepository<ProductMetricsJpaEntity, Long> {

    @Modifying
    @Query(
        """
        INSERT INTO product_metrics (product_id, like_count, order_count, sales_amount, view_count, created_at, updated_at)
        VALUES (:productId, :likeCount, 0, 0, 0, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            like_count = like_count + :likeCount,
            updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsertLikeCount(productId: Long, likeCount: Long)

    @Modifying
    @Query(
        """
        INSERT INTO product_metrics (product_id, like_count, order_count, sales_amount, view_count, created_at, updated_at)
        VALUES (:productId, 0, :orderCount, :salesAmount, 0, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            order_count = order_count + :orderCount,
            sales_amount = sales_amount + :salesAmount,
            updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsertOrderMetrics(productId: Long, orderCount: Long, salesAmount: Long)

    @Modifying
    @Query(
        """
        INSERT INTO product_metrics (product_id, like_count, order_count, sales_amount, view_count, created_at, updated_at)
        VALUES (:productId, 0, 0, 0, :viewCount, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            view_count = view_count + :viewCount,
            updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsertViewCount(productId: Long, viewCount: Long)
}
