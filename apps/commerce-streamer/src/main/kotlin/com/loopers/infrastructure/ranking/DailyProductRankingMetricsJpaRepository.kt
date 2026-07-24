package com.loopers.infrastructure.ranking

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface DailyProductRankingMetricsJpaRepository :
    JpaRepository<DailyProductRankingMetricsJpaEntity, DailyProductRankingMetricsId> {

    @Modifying
    @Query(
        """
        INSERT INTO daily_product_ranking_metrics
            (product_id, metric_date, view_count, like_count, order_count, sales_amount, ranking_score, created_at, updated_at)
        VALUES (:productId, :metricDate, :viewCount, :likeCount, :orderCount, :salesAmount, :rankingScore, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            view_count = view_count + :viewCount,
            like_count = like_count + :likeCount,
            order_count = order_count + :orderCount,
            sales_amount = sales_amount + :salesAmount,
            ranking_score = ranking_score + :rankingScore,
            updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        productId: Long,
        metricDate: LocalDate,
        viewCount: Long,
        likeCount: Long,
        orderCount: Long,
        salesAmount: Long,
        rankingScore: Double,
    )

    @Query(
        """
        SELECT e FROM DailyProductRankingMetricsJpaEntity e
        WHERE e.metricDate = :metricDate
          AND e.productId IN :productIds
        """,
    )
    fun findByMetricDateAndProductIdIn(
        metricDate: LocalDate,
        productIds: List<Long>,
    ): List<DailyProductRankingMetricsJpaEntity>
}
