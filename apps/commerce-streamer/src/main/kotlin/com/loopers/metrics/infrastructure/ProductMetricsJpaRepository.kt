package com.loopers.metrics.infrastructure

import com.loopers.metrics.domain.ProductMetrics
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProductMetricsJpaRepository : JpaRepository<ProductMetrics, Long> {
    @Modifying
    @Query(
        value = """
            insert into product_metrics (product_id, like_count, sales_count, view_count, created_at, updated_at)
            values (:productId, :likeDelta, :salesDelta, :viewDelta, now(), now())
            on duplicate key update
                like_count = like_count + :likeDelta,
                sales_count = sales_count + :salesDelta,
                view_count = view_count + :viewDelta,
                updated_at = now()
        """,
        nativeQuery = true,
    )
    fun upsertDelta(productId: Long, likeDelta: Long, salesDelta: Long, viewDelta: Long): Int
}
