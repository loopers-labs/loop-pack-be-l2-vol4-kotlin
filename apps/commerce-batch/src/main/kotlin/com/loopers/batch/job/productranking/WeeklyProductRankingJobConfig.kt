package com.loopers.batch.job.productranking

import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.productmetric.ProductMetricWeeklyRepository
import com.loopers.domain.productrank.ProductRankWeeklyRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.database.JdbcBatchItemWriter
import org.springframework.batch.item.database.JdbcCursorItemReader
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.PlatformTransactionManager
import java.sql.Date
import java.time.LocalDate
import javax.sql.DataSource

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyProductRankingJobConfig.JOB_NAME)
@Configuration
class WeeklyProductRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSource: DataSource,
    private val properties: ProductRankingBatchProperties,
    private val periodPolicy: ProductRankingPeriodPolicy,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val weightReader: ProductRankingWeightReader,
    private val productMetricWeeklyRepository: ProductMetricWeeklyRepository,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) {
    @Bean(JOB_NAME)
    fun weeklyProductRankingJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .validator(ProductRankingJobParametersValidator(ProductRankingPeriod.WEEKLY, periodPolicy))
            .start(weeklyMetricAggregationStep())
            .next(weeklyScoreMaterializationStep())
            .listener(jobListener)
            .listener(ProductRankingWeightSnapshotListener(weightReader))
            .listener(WeeklyProductRankingCacheEvictListener(redisTemplate))
            .build()
    }

    @Bean(STEP_METRIC_AGGREGATION)
    fun weeklyMetricAggregationStep(): Step {
        return StepBuilder(STEP_METRIC_AGGREGATION, jobRepository)
            .chunk<ProductMetricAggregate, ProductMetricAggregate>(properties.metric.chunkSize, transactionManager)
            .reader(weeklyDailyMetricAggregateReader(null))
            .writer(weeklyMetricWriter())
            .listener(weeklyMetricCleanupListener(null))
            .listener(stepMonitorListener)
            .build()
    }

    @Bean(STEP_SCORE_MATERIALIZATION)
    fun weeklyScoreMaterializationStep(): Step {
        return StepBuilder(STEP_SCORE_MATERIALIZATION, jobRepository)
            .chunk<ProductMetricAggregate, ProductRankingScore>(properties.metric.chunkSize, transactionManager)
            .reader(weeklyMetricReader(null))
            .processor(weeklyScoreProcessor(null, null, null))
            .writer(weeklyRankWriter())
            .listener(weeklyRankCleanupListener(null))
            .listener(stepMonitorListener)
            .build()
    }

    @StepScope
    @Bean(READER_DAILY_METRIC_AGGREGATE)
    fun weeklyDailyMetricAggregateReader(
        @Value("#{jobParameters['baseDate']}") baseDateParameter: Any?,
    ): JdbcCursorItemReader<ProductMetricAggregate> {
        val range = periodPolicy.weekly(parseBaseDate(baseDateParameter))
        return JdbcCursorItemReaderBuilder<ProductMetricAggregate>()
            .name(READER_DAILY_METRIC_AGGREGATE)
            .dataSource(dataSource)
            .fetchSize(properties.metric.fetchSize)
            .sql(
                """
                SELECT
                    product_id,
                    SUM(view_count) AS view_count,
                    SUM(like_count) AS like_count,
                    SUM(sales_amount) AS sales_amount
                FROM product_metric_daily
                WHERE metric_date >= ?
                  AND metric_date < ?
                GROUP BY product_id
                ORDER BY product_id
                """.trimIndent(),
            )
            .preparedStatementSetter { statement ->
                statement.setDate(1, Date.valueOf(range.sourceStart))
                statement.setDate(2, Date.valueOf(range.sourceEndExclusive))
            }
            .rowMapper(productMetricAggregateRowMapper(range.baseDate))
            .build()
    }

    @Bean(WRITER_WEEKLY_METRIC)
    fun weeklyMetricWriter(): JdbcBatchItemWriter<ProductMetricAggregate> {
        return JdbcBatchItemWriterBuilder<ProductMetricAggregate>()
            .dataSource(dataSource)
            .sql(
                """
                INSERT INTO product_metric_weekly (
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
                """.trimIndent(),
            )
            .beanMapped()
            .build()
    }

    @StepScope
    @Bean(READER_WEEKLY_METRIC)
    fun weeklyMetricReader(
        @Value("#{jobParameters['baseDate']}") baseDateParameter: Any?,
    ): JdbcCursorItemReader<ProductMetricAggregate> {
        val baseDate = parseBaseDate(baseDateParameter)
        return JdbcCursorItemReaderBuilder<ProductMetricAggregate>()
            .name(READER_WEEKLY_METRIC)
            .dataSource(dataSource)
            .fetchSize(properties.metric.fetchSize)
            .sql(
                """
                SELECT
                    base_date,
                    product_id,
                    view_count,
                    like_count,
                    sales_amount
                FROM product_metric_weekly
                WHERE base_date = ?
                ORDER BY product_id
                """.trimIndent(),
            )
            .preparedStatementSetter { statement ->
                statement.setDate(1, Date.valueOf(baseDate))
            }
            .rowMapper(productMetricAggregateRowMapper())
            .build()
    }

    @StepScope
    @Bean(PROCESSOR_WEEKLY_SCORE)
    fun weeklyScoreProcessor(
        @Value("#{jobExecutionContext['${ProductRankingWeights.VIEW_CONTEXT_KEY}']}") viewWeight: Double?,
        @Value("#{jobExecutionContext['${ProductRankingWeights.LIKE_CONTEXT_KEY}']}") likeWeight: Double?,
        @Value("#{jobExecutionContext['${ProductRankingWeights.SALES_CONTEXT_KEY}']}") salesWeight: Double?,
    ): ItemProcessor<ProductMetricAggregate, ProductRankingScore> {
        return ProductRankingScoreProcessor(
            viewWeight = requireNotNull(viewWeight),
            likeWeight = requireNotNull(likeWeight),
            salesWeight = requireNotNull(salesWeight),
        )
    }

    @Bean(WRITER_WEEKLY_RANK)
    fun weeklyRankWriter(): JdbcBatchItemWriter<ProductRankingScore> {
        return JdbcBatchItemWriterBuilder<ProductRankingScore>()
            .dataSource(dataSource)
            .sql(
                """
                INSERT INTO mv_product_rank_weekly (
                    base_date,
                    product_id,
                    ranking_score,
                    created_at,
                    updated_at
                ) VALUES (
                    :baseDate,
                    :productId,
                    :rankingScore,
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6)
                )
                ON DUPLICATE KEY UPDATE
                    ranking_score = VALUES(ranking_score),
                    updated_at = CURRENT_TIMESTAMP(6)
                """.trimIndent(),
            )
            .beanMapped()
            .build()
    }

    @StepScope
    @Bean(LISTENER_WEEKLY_METRIC_CLEANUP)
    fun weeklyMetricCleanupListener(
        @Value("#{jobParameters['baseDate']}") baseDateParameter: Any?,
    ): StepExecutionListener {
        return object : StepExecutionListener {
            override fun beforeStep(stepExecution: StepExecution) {
                productMetricWeeklyRepository.deleteByBaseDate(parseBaseDate(baseDateParameter))
            }
        }
    }

    @StepScope
    @Bean(LISTENER_WEEKLY_RANK_CLEANUP)
    fun weeklyRankCleanupListener(
        @Value("#{jobParameters['baseDate']}") baseDateParameter: Any?,
    ): StepExecutionListener {
        return object : StepExecutionListener {
            override fun beforeStep(stepExecution: StepExecution) {
                productRankWeeklyRepository.deleteByBaseDate(parseBaseDate(baseDateParameter))
            }
        }
    }

    private fun productMetricAggregateRowMapper(baseDateOverride: LocalDate? = null): RowMapper<ProductMetricAggregate> {
        return RowMapper { resultSet, _ ->
            ProductMetricAggregate(
                baseDate = baseDateOverride ?: resultSet.getDate("base_date").toLocalDate(),
                productId = resultSet.getLong("product_id"),
                viewCount = resultSet.getLong("view_count"),
                likeCount = resultSet.getLong("like_count"),
                salesAmount = resultSet.getLong("sales_amount"),
            )
        }
    }

    private fun parseBaseDate(baseDateParameter: Any?): LocalDate {
        return when (val value = requireNotNull(baseDateParameter) { "baseDate is required." }) {
            is LocalDate -> value
            is String -> LocalDate.parse(value)
            else -> error("baseDate must be LocalDate or yyyy-MM-dd String.")
        }
    }

    companion object {
        const val JOB_NAME = "weeklyProductRankingJob"
        private const val STEP_METRIC_AGGREGATION = "weeklyProductMetricAggregationStep"
        private const val STEP_SCORE_MATERIALIZATION = "weeklyProductScoreMaterializationStep"
        private const val READER_DAILY_METRIC_AGGREGATE = "weeklyDailyMetricAggregateReader"
        private const val READER_WEEKLY_METRIC = "weeklyMetricReader"
        private const val WRITER_WEEKLY_METRIC = "weeklyMetricWriter"
        private const val WRITER_WEEKLY_RANK = "weeklyRankWriter"
        private const val PROCESSOR_WEEKLY_SCORE = "weeklyScoreProcessor"
        private const val LISTENER_WEEKLY_METRIC_CLEANUP = "weeklyMetricCleanupListener"
        private const val LISTENER_WEEKLY_RANK_CLEANUP = "weeklyRankCleanupListener"
    }
}
