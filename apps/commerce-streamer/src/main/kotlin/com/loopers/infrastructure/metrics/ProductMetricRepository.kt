package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetricsModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductMetricRepository : JpaRepository<ProductMetricsModel, Long> {
    @Modifying(clearAutomatically = true)
    @Query(value = """
    INSERT INTO product_metrics (product_id, like_count, version)
    VALUES (:productId, GREATEST(:delta, 0), :version)
    ON DUPLICATE KEY UPDATE
        like_count = GREATEST(0, like_count + :delta),
        version = GREATEST(version, :version)
    """, nativeQuery = true)
    fun upsertLikeCount(productId: Long, delta: Int, version: Long): Int

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
    INSERT INTO product_metrics (product_id, sales_count, version)
    VALUES (:productId, GREATEST(:delta, 0), :version)
    ON DUPLICATE KEY UPDATE
        sales_count = GREATEST(0, sales_count + :delta),
        version = GREATEST(version, :version)
    """,
        nativeQuery = true,
    )
    fun upsertSalesCount(productId: Long, delta: Int, version: Long): Int

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
    INSERT INTO product_metrics (product_id, view_count, version)
    VALUES (:productId, GREATEST(:delta, 0), :version)
    ON DUPLICATE KEY UPDATE
        view_count = GREATEST(0, view_count + :delta),
        version = GREATEST(version, :version)
    """,
        nativeQuery = true,
    )
    fun upsertViewCount(productId: Long, delta: Int, version: Long): Int
}
