package com.loopers.batch.job.productrank

import com.loopers.batch.job.productrank.item.AggregatedMetricRow
import com.loopers.batch.job.productrank.item.ProductScore
import com.loopers.batch.job.productrank.step.ProductRankReaders
import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.database.JdbcBatchItemWriter
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * 변형 A — DB 집계. 기간 전체를 GROUP BY로 상품별 집계해 페이징으로 읽고,
 * 점수 계산 후 staging에 INSERT한다. 페이지마다 집계가 반복되는 비용이 관측 포인트.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankGroupByJobConfig.JOB_NAME)
@Configuration
class ProductRankGroupByJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val chunkListener: ChunkListener,
    private val dataSource: DataSource,
    private val rankWeightLoader: RankWeightLoader,
    @Value("\${batch.product-rank.chunk-size:1000}") private val chunkSize: Int,
) {
    companion object {
        const val JOB_NAME = "productRankGroupByJob"
        private const val STEP_AGGREGATE = "productRankGroupByAggregateStep"
    }

    @Bean(JOB_NAME)
    fun productRankGroupByJob(
        @Qualifier(ProductRankSharedStepConfig.CLEAR_STAGING_STEP) clearStagingStep: Step,
        @Qualifier(ProductRankSharedStepConfig.RANK_CONFIRM_STEP) rankConfirmStep: Step,
    ): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .validator(ProductRankJobParams.validator())
            .start(clearStagingStep)
            .next(aggregateStep())
            .next(rankConfirmStep)
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_AGGREGATE)
    fun aggregateStep(
        @Value("#{jobParameters['period']}") period: String? = null,
        @Value("#{jobParameters['targetDate']}") targetDate: String? = null,
    ): Step {
        val window = ProductRankJobParams.resolveWindow(ProductRankJobParams.resolvePeriod(period), targetDate)
        val weights = rankWeightLoader.loadActive()
        return StepBuilder(STEP_AGGREGATE, jobRepository)
            .chunk<AggregatedMetricRow, ProductScore>(chunkSize, transactionManager)
            .reader(ProductRankReaders.groupByReader(dataSource, window, chunkSize))
            .processor(
                ItemProcessor { row ->
                    ProductScore(
                        productId = row.productId,
                        score = weights.scoreOf(row.viewCount, row.likeCount, row.salesCount),
                    )
                },
            )
            .writer(stagingInsertWriter())
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build()
    }

    private fun stagingInsertWriter(): JdbcBatchItemWriter<ProductScore> =
        JdbcBatchItemWriterBuilder<ProductScore>()
            .dataSource(dataSource)
            .sql("INSERT INTO product_rank_staging (product_id, score) VALUES (:productId, :score)")
            .beanMapped()
            .build()
            .apply { afterPropertiesSet() }
}
