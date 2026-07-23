package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.AggregationPeriod
import com.loopers.batch.job.ranking.MonthlyRankingJobConfig
import com.loopers.batch.metrics.ProductMetricsMonthly
import com.loopers.config.redis.RedisConfig
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.time.LocalDate

@Component
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingItemWriter(
    private val jdbcTemplate: JdbcTemplate,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("#{jobParameters['baseDate']}") baseDate: String,
) : ItemWriter<ProductMetricsMonthly>, StepExecutionListener {
    private val zsetKey = ZSET_KEY_PREFIX + AggregationPeriod.monthlyOf(LocalDate.parse(baseDate)).key

    override fun beforeStep(stepExecution: StepExecution) {
        redisTemplate.delete(zsetKey)
    }

    override fun write(chunk: Chunk<out ProductMetricsMonthly>) {
        val items = chunk.items
        if (items.isEmpty()) return

        jdbcTemplate.batchUpdate(
            UPSERT_SQL,
            object : BatchPreparedStatementSetter {
                override fun setValues(ps: PreparedStatement, i: Int) {
                    val item = items[i]
                    ps.setLong(1, item.productId)
                    ps.setString(2, item.yearMonth)
                    ps.setInt(3, item.likeCount)
                    ps.setInt(4, item.salesCount)
                    ps.setInt(5, item.viewCount)
                    ps.setDouble(6, item.score)
                    ps.setObject(7, item.updatedAt.toLocalDateTime())
                }

                override fun getBatchSize(): Int = items.size
            },
        )

        val tuples = items.map {
            ZSetOperations.TypedTuple.of(it.productId.toString(), it.score)
        }.toSet()
        redisTemplate.opsForZSet().add(zsetKey, tuples)
    }

    companion object {
        private const val ZSET_KEY_PREFIX = "ranking:monthly:"
        private val UPSERT_SQL = """
            INSERT INTO product_metrics_monthly
                (product_id, month_key, like_count, sales_count, view_count, score, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                like_count = VALUES(like_count),
                sales_count = VALUES(sales_count),
                view_count = VALUES(view_count),
                score = VALUES(score),
                updated_at = VALUES(updated_at)
        """.trimIndent()
    }
}