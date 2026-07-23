package com.loopers.batch.job.productrank

import com.loopers.batch.job.productrank.item.ProductScore
import com.loopers.batch.job.productrank.item.RawMetricRow
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
 * 변형 B1 — 앱→DB 누적. 원시 행을 그대로 읽어 행 단위 점수 delta를 계산하고,
 * staging에 ON DUPLICATE KEY UPDATE로 누적한다. 전송량(상품수×일수×타입)과 upsert 쓰기가 관측 포인트.
 * 재실행 멱등성은 선행 clear Step(staging TRUNCATE)이 보장한다.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankStagingUpsertJobConfig.JOB_NAME)
@Configuration
class ProductRankStagingUpsertJobConfig(
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
        const val JOB_NAME = "productRankStagingUpsertJob"
        private const val STEP_AGGREGATE = "productRankStagingUpsertAggregateStep"
    }

    @Bean(JOB_NAME)
    fun productRankStagingUpsertJob(
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
            .chunk<RawMetricRow, ProductScore>(chunkSize, transactionManager)
            .reader(ProductRankReaders.rawReader(dataSource, window, chunkSize))
            .processor(
                ItemProcessor { row ->
                    val delta = row.count * weights.weightFor(row.type)
                    // 모르는 타입(가중치 0)은 누적할 것이 없다 — null 반환으로 스킵
                    if (delta == 0L) null else ProductScore(productId = row.productId, score = delta)
                },
            )
            .writer(stagingUpsertWriter())
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build()
    }

    private fun stagingUpsertWriter(): JdbcBatchItemWriter<ProductScore> =
        JdbcBatchItemWriterBuilder<ProductScore>()
            .dataSource(dataSource)
            .sql(
                """
                INSERT INTO product_rank_staging (product_id, score) VALUES (:productId, :score)
                ON DUPLICATE KEY UPDATE score = score + :score
                """.trimIndent(),
            )
            .beanMapped()
            // upsert의 affected rows는 insert=1/update=2로 변동해 검증 의미가 없다
            .assertUpdates(false)
            .build()
            .apply { afterPropertiesSet() }
}
