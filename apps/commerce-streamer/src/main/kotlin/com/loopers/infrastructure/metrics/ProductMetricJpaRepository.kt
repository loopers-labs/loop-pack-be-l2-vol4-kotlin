package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetric
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductMetricJpaRepository : JpaRepository<ProductMetric, Long> {
    @Transactional
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO product_metrics (product_id, like_count, sales_count, view_count)
            VALUES (:productId, GREATEST(0, :likeDelta), GREATEST(0, :salesDelta), GREATEST(0, :viewDelta))
            ON DUPLICATE KEY UPDATE
                like_count = GREATEST(0, like_count + :likeDelta),
                sales_count = GREATEST(0, sales_count + :salesDelta),
                view_count = GREATEST(0, view_count + :viewDelta)
        """,
    )
    fun upsertDelta(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Long,
        @Param("salesDelta") salesDelta: Long,
        @Param("viewDelta") viewDelta: Long,
    )
}
