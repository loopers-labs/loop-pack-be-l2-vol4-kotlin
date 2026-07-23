package com.loopers.batch.job.productranking

import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.productrank.ProductRankMonthlyRepository
import com.loopers.domain.productrank.ProductRankPublicationRepository
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.PlatformTransactionManager
import java.sql.Date
import java.time.LocalDate
import javax.sql.DataSource

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = MonthlyProductRankingJobConfig.JOB_NAME)
@Configuration
class MonthlyProductRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSource: DataSource,
    private val properties: ProductRankingBatchProperties,
    private val periodPolicy: ProductRankingPeriodPolicy,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val weightReader: ProductRankingWeightReader,
    private val productRankMonthlyRepository: ProductRankMonthlyRepository,
    private val productRankPublicationRepository: ProductRankPublicationRepository,
) {
    @Bean(JOB_NAME)
    fun monthlyProductRankingJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .validator(ProductRankingJobParametersValidator(ProductRankingPeriod.MONTHLY, periodPolicy))
            .start(monthlyScoreMaterializationStep())
            .listener(jobListener)
            .listener(ProductRankingWeightSnapshotListener(weightReader))
            .listener(ProductRankingPublicationListener(ProductRankingPeriod.MONTHLY, productRankPublicationRepository))
            .build()
    }

    @Bean(STEP_SCORE_MATERIALIZATION)
    fun monthlyScoreMaterializationStep(): Step {
        return StepBuilder(STEP_SCORE_MATERIALIZATION, jobRepository)
            .chunk<ProductMetricAggregate, ProductRankingScore>(properties.metric.chunkSize, transactionManager)
            .reader(monthlyDailyMetricAggregateReader(null))
            .processor(monthlyScoreProcessor(null, null, null))
            .writer(monthlyRankWriter())
            .listener(monthlyRankCleanupListener(null))
            .listener(stepMonitorListener)
            .build()
    }

    @StepScope
    @Bean(READER_DAILY_METRIC_AGGREGATE)
    fun monthlyDailyMetricAggregateReader(
        @Value("#{jobParameters['baseDate']}") baseDateParameter: Any?,
    ): JdbcCursorItemReader<ProductMetricAggregate> {
        val range = periodPolicy.monthly(parseBaseDate(baseDateParameter))
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

    @StepScope
    @Bean(PROCESSOR_MONTHLY_SCORE)
    fun monthlyScoreProcessor(
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

    @Bean(WRITER_MONTHLY_RANK)
    fun monthlyRankWriter(): JdbcBatchItemWriter<ProductRankingScore> {
        return JdbcBatchItemWriterBuilder<ProductRankingScore>()
            .dataSource(dataSource)
            .sql(
                """
                INSERT INTO mv_product_rank_monthly (
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
    @Bean(LISTENER_MONTHLY_RANK_CLEANUP)
    fun monthlyRankCleanupListener(
        @Value("#{jobParameters['baseDate']}") baseDateParameter: Any?,
    ): StepExecutionListener {
        return object : StepExecutionListener {
            override fun beforeStep(stepExecution: StepExecution) {
                productRankMonthlyRepository.deleteByBaseDate(parseBaseDate(baseDateParameter))
            }
        }
    }

    private fun productMetricAggregateRowMapper(baseDate: LocalDate): RowMapper<ProductMetricAggregate> {
        return RowMapper { resultSet, _ ->
            ProductMetricAggregate(
                baseDate = baseDate,
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
        const val JOB_NAME = "monthlyProductRankingJob"
        private const val STEP_SCORE_MATERIALIZATION = "monthlyProductScoreMaterializationStep"
        private const val READER_DAILY_METRIC_AGGREGATE = "monthlyDailyMetricAggregateReader"
        private const val WRITER_MONTHLY_RANK = "monthlyRankWriter"
        private const val PROCESSOR_MONTHLY_SCORE = "monthlyScoreProcessor"
        private const val LISTENER_MONTHLY_RANK_CLEANUP = "monthlyRankCleanupListener"
    }
}
