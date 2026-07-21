package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.AggregationPeriod
import com.loopers.batch.job.ranking.ProductMetricsAggregate
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.database.JdbcCursorItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.time.LocalDate
import javax.sql.DataSource

@Component
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingReader(
    dataSource: DataSource,
    @Value("#{jobParameters['baseDate']}") baseDate: String,
) : JdbcCursorItemReader<ProductMetricsAggregate>() {
    init {
        val period = AggregationPeriod.weeklyOf(LocalDate.parse(baseDate))
        setName("weeklyRankingReader")
        setDataSource(dataSource)
        setSql(
            """
            SELECT product_id,
                   SUM(like_count)  AS like_count,
                   SUM(sales_count) AS sales_count,
                   SUM(view_count)  AS view_count
            FROM product_metrics_daily
            WHERE metric_date BETWEEN ? AND ?
            GROUP BY product_id
            """.trimIndent(),
        )
        setPreparedStatementSetter { ps ->
            ps.setObject(1, period.from)
            ps.setObject(2, period.to)
        }
        setRowMapper { rs, _ ->
            ProductMetricsAggregate(
                productId = rs.getLong("product_id"),
                likeCount = rs.getInt("like_count"),
                salesCount = rs.getInt("sales_count"),
                viewCount = rs.getInt("view_count"),
            )
        }
    }
}
