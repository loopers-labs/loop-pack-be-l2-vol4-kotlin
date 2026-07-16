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
            values (:productId, :likeChange, :salesChange, :viewChange, now(), now())
            on duplicate key update
                like_count = like_count + :likeChange,
                sales_count = sales_count + :salesChange,
                view_count = view_count + :viewChange,
                updated_at = now()
        """,
        nativeQuery = true,
    )
    fun upsertChanges(productId: Long, likeChange: Long, salesChange: Long, viewChange: Long): Int
}
