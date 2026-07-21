package com.loopers.infrastructure.productmetric.repository

import com.loopers.infrastructure.productmetric.entity.ProductMetricDailyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ProductMetricDailyJpaRepository : JpaRepository<ProductMetricDailyEntity, Long> {
    fun findByMetricDateAndProductId(
        metricDate: LocalDate,
        productId: Long,
    ): ProductMetricDailyEntity?

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO product_metric_daily (
                metric_date,
                product_id,
                view_count,
                like_count,
                sales_amount,
                created_at,
                updated_at
            ) VALUES (
                :metricDate,
                :productId,
                :viewCountDelta,
                :likeCountDelta,
                :salesAmountDelta,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                view_count = view_count + VALUES(view_count),
                like_count = like_count + VALUES(like_count),
                sales_amount = sales_amount + VALUES(sales_amount),
                updated_at = CURRENT_TIMESTAMP(6)
        """,
    )
    fun upsertIncrement(
        @Param("metricDate") metricDate: LocalDate,
        @Param("productId") productId: Long,
        @Param("viewCountDelta") viewCountDelta: Long,
        @Param("likeCountDelta") likeCountDelta: Long,
        @Param("salesAmountDelta") salesAmountDelta: Long,
    ): Int
}
