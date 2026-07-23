package com.loopers.batch.job.metrics.step

import com.loopers.batch.job.metrics.DailyMetricsJobConfig
import com.loopers.batch.job.metrics.ProductDailyDelta
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.database.JdbcCursorItemReader
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = DailyMetricsJobConfig.JOB_NAME)
class DailyMetricsReader(
    dataSource: DataSource,
) : JdbcCursorItemReader<ProductDailyDelta>() {
    init {
        setName("dailyMetricsReader")
        setDataSource(dataSource)
        setSql(
            """
            SELECT m.product_id                                    AS product_id,
                   m.like_count                                    AS cum_like,
                   m.sales_count                                   AS cum_sales,
                   m.view_count                                    AS cum_view,
                   m.like_count  - COALESCE(s.like_count, 0)       AS delta_like,
                   m.sales_count - COALESCE(s.sales_count, 0)      AS delta_sales,
                   m.view_count  - COALESCE(s.view_count, 0)       AS delta_view
            FROM product_metrics m
            LEFT JOIN product_metrics_snapshot s ON s.product_id = m.product_id
            WHERE (m.like_count  - COALESCE(s.like_count, 0))  <> 0
               OR (m.sales_count - COALESCE(s.sales_count, 0)) <> 0
               OR (m.view_count  - COALESCE(s.view_count, 0))  <> 0
            """.trimIndent(),
        )
        setRowMapper(
            RowMapper { rs, _ ->
                ProductDailyDelta(
                    productId = rs.getLong("product_id"),
                    cumulativeLike = rs.getInt("cum_like"),
                    cumulativeSales = rs.getInt("cum_sales"),
                    cumulativeView = rs.getInt("cum_view"),
                    deltaLike = rs.getInt("delta_like"),
                    deltaSales = rs.getInt("delta_sales"),
                    deltaView = rs.getInt("delta_view"),
                )
            },
        )
    }
}