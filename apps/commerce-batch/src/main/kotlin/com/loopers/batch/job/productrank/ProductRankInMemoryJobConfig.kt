package com.loopers.batch.job.productrank

import com.loopers.batch.job.productrank.item.ProductScore
import com.loopers.batch.job.productrank.item.RawMetricRow
import com.loopers.batch.job.productrank.step.InMemoryRankConfirmTasklet
import com.loopers.batch.job.productrank.step.ProductRankReaders
import com.loopers.batch.job.productrank.step.ProductScoreAccumulator
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
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * 변형 B2 — 앱 인메모리 집계. 원시 행을 읽어 @JobScope Map에 누적하고(DB 쓰기 없음),
 * 마지막에 메모리에서 TOP 100을 뽑아 MV에 반영한다. staging을 쓰지 않으므로 clear Step도 없다.
 * 쓰기 I/O 제거 vs 힙 사용·재시작 불가의 트레이드오프가 관측 포인트.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankInMemoryJobConfig.JOB_NAME)
@Configuration
class ProductRankInMemoryJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val chunkListener: ChunkListener,
    private val dataSource: DataSource,
    private val rankWeightLoader: RankWeightLoader,
    private val productScoreAccumulator: ProductScoreAccumulator,
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${batch.product-rank.chunk-size:1000}") private val chunkSize: Int,
) {
    companion object {
        const val JOB_NAME = "productRankInMemoryJob"
        private const val STEP_AGGREGATE = "productRankInMemoryAggregateStep"
        private const val STEP_CONFIRM = "productRankInMemoryConfirmStep"
    }

    @Bean(JOB_NAME)
    fun productRankInMemoryJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .validator(ProductRankJobParams.validator())
            .start(aggregateStep())
            .next(confirmStep())
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
                    if (delta == 0L) null else ProductScore(productId = row.productId, score = delta)
                },
            )
            .writer(
                ItemWriter { chunk ->
                    chunk.items.forEach { productScoreAccumulator.accumulate(it.productId, it.score) }
                },
            )
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build()
    }

    @JobScope
    @Bean(STEP_CONFIRM)
    fun confirmStep(
        @Value("#{jobParameters['period']}") period: String? = null,
        @Value("#{jobParameters['targetDate']}") targetDate: String? = null,
    ): Step {
        val rankPeriod = ProductRankJobParams.resolvePeriod(period)
        val window = ProductRankJobParams.resolveWindow(rankPeriod, targetDate)
        return StepBuilder(STEP_CONFIRM, jobRepository)
            .tasklet(
                InMemoryRankConfirmTasklet(jdbcTemplate, rankPeriod, window.aggregatedDate, productScoreAccumulator),
                transactionManager,
            )
            .listener(stepMonitorListener)
            .build()
    }
}
