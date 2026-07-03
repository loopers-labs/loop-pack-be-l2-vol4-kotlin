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
            VALUES (:productId, :likeDelta, :salesDelta, :viewDelta)
            ON DUPLICATE KEY UPDATE
                like_count = like_count + :likeDelta,
                sales_count = sales_count + :salesDelta,
                view_count = view_count + :viewDelta
        """,
    )
    fun upsertDelta(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Long,
        @Param("salesDelta") salesDelta: Long,
        @Param("viewDelta") viewDelta: Long,
    )
}
