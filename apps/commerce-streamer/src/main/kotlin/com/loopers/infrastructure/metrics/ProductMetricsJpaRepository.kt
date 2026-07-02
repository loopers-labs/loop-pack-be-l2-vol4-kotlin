package com.loopers.infrastructure.metrics

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductMetricsJpaRepository : JpaRepository<ProductMetricsJpaEntity, Long> {

    @Modifying
    @Query(
        """
        INSERT INTO product_metrics (product_id, like_count, order_count, sales_amount, version, created_at, updated_at)
        VALUES (:productId, :likeCount, 0, 0, :version, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            like_count = CASE WHEN :version > version THEN like_count + :likeCount ELSE like_count END,
            version = GREATEST(version, :version),
            updated_at = CASE WHEN :version > version THEN NOW() ELSE updated_at END
        """,
        nativeQuery = true,
    )
    fun upsertLikeCount(productId: Long, likeCount: Long, version: Long)

    @Modifying
    @Query(
        """
        INSERT INTO product_metrics (product_id, like_count, order_count, sales_amount, version, created_at, updated_at)
        VALUES (:productId, 0, :orderCount, :salesAmount, :version, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            order_count = CASE WHEN :version > version THEN order_count + :orderCount ELSE order_count END,
            sales_amount = CASE WHEN :version > version THEN sales_amount + :salesAmount ELSE sales_amount END,
            version = GREATEST(version, :version),
            updated_at = CASE WHEN :version > version THEN NOW() ELSE updated_at END
        """,
        nativeQuery = true,
    )
    fun upsertOrderMetrics(productId: Long, orderCount: Long, salesAmount: Long, version: Long)
}
