package com.loopers.infrastructure.productmetric.repository

import com.loopers.domain.productmetric.ProductMetricMonthly
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ProductMetricMonthlyJpaRepository : JpaRepository<ProductMetricMonthly, Long> {
    fun findByBaseDateAndProductId(
        baseDate: LocalDate,
        productId: Long,
    ): ProductMetricMonthly?

    @Modifying
    fun deleteByBaseDate(baseDate: LocalDate): Int

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO product_metric_monthly (
                base_date,
                product_id,
                view_count,
                like_count,
                sales_amount,
                created_at,
                updated_at
            ) VALUES (
                :baseDate,
                :productId,
                :viewCount,
                :likeCount,
                :salesAmount,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                view_count = VALUES(view_count),
                like_count = VALUES(like_count),
                sales_amount = VALUES(sales_amount),
                updated_at = CURRENT_TIMESTAMP(6)
        """,
    )
    fun upsert(
        @Param("baseDate") baseDate: LocalDate,
        @Param("productId") productId: Long,
        @Param("viewCount") viewCount: Long,
        @Param("likeCount") likeCount: Long,
        @Param("salesAmount") salesAmount: Long,
    ): Int
}
