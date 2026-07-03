package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetricsModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductMetricsJpaRepository : JpaRepository<ProductMetricsModel, Long> {
    /**
     * 원자적 upsert. 행이 없으면 INSERT, 있으면 각 카운터를 delta 만큼 증감한다.
     * MySQL row-level lock 으로 동시 갱신 시에도 손실 없이 누적된다.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, updated_at)
            VALUES (:productId, :likeDelta, :viewDelta, :salesDelta, NOW())
            ON DUPLICATE KEY UPDATE
                like_count = like_count + :likeDelta,
                view_count = view_count + :viewDelta,
                sales_count = sales_count + :salesDelta,
                updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Long,
        @Param("viewDelta") viewDelta: Long,
        @Param("salesDelta") salesDelta: Long,
    ): Int
}
