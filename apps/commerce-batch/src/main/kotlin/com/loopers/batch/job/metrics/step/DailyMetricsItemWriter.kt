package com.loopers.batch.job.metrics.step

import com.loopers.batch.job.metrics.DailyMetricsJobConfig
import com.loopers.batch.job.metrics.ProductDailyDelta
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.time.LocalDate

@Component
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = DailyMetricsJobConfig.JOB_NAME)
class DailyMetricsItemWriter(
    private val jdbcTemplate: JdbcTemplate,
    @Value("#{jobParameters['baseDate']}") baseDate: String,
) : ItemWriter<ProductDailyDelta> {
    private val metricDate: LocalDate = LocalDate.parse(baseDate)

    override fun write(chunk: Chunk<out ProductDailyDelta>) {
        val items = chunk.items
        if (items.isEmpty()) return

        // 1) 당일 델타를 product_metrics_daily 에 적재
        jdbcTemplate.batchUpdate(
            DAILY_UPSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val item = items[i]
                    ps.setLong(1, item.productId)
                    ps.setObject(2, metricDate)
                    ps.setInt(3, item.deltaLike)
                    ps.setInt(4, item.deltaSales)
                    ps.setInt(5, item.deltaView)
                }

                override fun getBatchSize(): Int = items.size
            },
        )

        // 2) 스냅샷을 현재 누적으로 전진 (다음 실행의 차분 기준)
        jdbcTemplate.batchUpdate(
            SNAPSHOT_UPSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val item = items[i]
                    ps.setLong(1, item.productId)
                    ps.setInt(2, item.cumulativeLike)
                    ps.setInt(3, item.cumulativeSales)
                    ps.setInt(4, item.cumulativeView)
                }

                override fun getBatchSize(): Int = items.size
            },
        )
    }

    companion object {
        private val DAILY_UPSERT_SQL = """
            INSERT INTO product_metrics_daily
                (product_id, metric_date, like_count, sales_count, view_count)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                like_count = VALUES(like_count),
                sales_count = VALUES(sales_count),
                view_count = VALUES(view_count)
        """.trimIndent()

        private val SNAPSHOT_UPSERT_SQL = """
            INSERT INTO product_metrics_snapshot
                (product_id, like_count, sales_count, view_count)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                like_count = VALUES(like_count),
                sales_count = VALUES(sales_count),
                view_count = VALUES(view_count)
        """.trimIndent()
    }
}