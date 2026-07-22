package com.loopers.batch.job.productrank

import com.loopers.domain.metrics.ProductPeriodMetrics
import com.loopers.domain.metrics.ProductSignalSummary
import com.loopers.domain.ranking.RankingPeriod
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.database.JdbcBatchItemWriter
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.batch.item.database.Order
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 주간 집계 스텝 2종 — 정리(해당 주 기간 키 delete) 후, 시간별 집계를 주 창으로 합산해 기간 집계 테이블에 적재한다.
 * 정리를 별도 스텝으로 두는 이유: 청크 스텝 재시작 시 정리가 다시 실행돼 이미 적재한 행을 지우는 사고를 막는다.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankWeeklyStepConfig.JOB_NAME)
@Configuration
class ProductRankWeeklyStepConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSource: DataSource,
    @Value("\${loopers.batch.product-rank.chunk-size:1000}") private val chunkSize: Int,
) {
    companion object {
        const val JOB_NAME = "productRankJob"
        const val WEEKLY_CLEAN_STEP = "weeklyCleanStep"
        const val WEEKLY_AGGREGATE_STEP = "weeklyAggregateStep"
        private const val WEEKLY_HOURLY_READER = "weeklyHourlyReader"
    }

    @Bean(WEEKLY_CLEAN_STEP)
    fun weeklyCleanStep(): Step = StepBuilder(WEEKLY_CLEAN_STEP, jobRepository)
        .tasklet(weeklyCleanTasklet(null), transactionManager)
        .build()

    @Bean
    @StepScope
    fun weeklyCleanTasklet(@Value("#{jobParameters['targetDate']}") targetDate: String?): Tasklet {
        val period = weeklyPeriodOf(targetDate)
        return Tasklet { _, _ ->
            JdbcTemplate(dataSource).update("DELETE FROM product_metrics_weekly WHERE period_key = ?", period.key)
            RepeatStatus.FINISHED
        }
    }

    @Bean(WEEKLY_AGGREGATE_STEP)
    fun weeklyAggregateStep(): Step = StepBuilder(WEEKLY_AGGREGATE_STEP, jobRepository)
        .chunk<ProductSignalSummary, ProductPeriodMetrics>(chunkSize, transactionManager)
        .reader(weeklyHourlyReader(null))
        .processor(weeklyPeriodProcessor(null))
        .writer(weeklyMetricsWriter())
        .build()

    @Bean(WEEKLY_HOURLY_READER)
    @StepScope
    fun weeklyHourlyReader(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): JdbcPagingItemReader<ProductSignalSummary> {
        val period = weeklyPeriodOf(targetDate)
        val queryProvider = SqlPagingQueryProviderFactoryBean().apply {
            setDataSource(dataSource)
            // 페이징 래퍼가 정렬 키를 바깥 쿼리에서 참조하므로 select 별칭(product_id)과 정렬 키 이름을 맞춘다.
            setSelectClause(
                "SELECT h.product_id AS product_id, SUM(h.view_count) AS view_count, " +
                    "SUM(h.like_count) AS like_count, SUM(h.order_quantity) AS order_quantity",
            )
            setFromClause("FROM product_metrics_hourly h")
            setWhereClause(
                "WHERE h.stat_hour >= :startAt AND h.stat_hour < :endAt " +
                    "AND h.product_id NOT IN (SELECT m.product_id FROM product_metrics m WHERE m.deleted_at IS NOT NULL)",
            )
            setGroupClause("GROUP BY h.product_id")
            setSortKeys(mapOf("product_id" to Order.ASCENDING))
        }
        return JdbcPagingItemReaderBuilder<ProductSignalSummary>()
            .name(WEEKLY_HOURLY_READER)
            .dataSource(dataSource)
            .queryProvider(queryProvider.`object`)
            .parameterValues(mapOf("startAt" to period.start, "endAt" to period.end))
            .pageSize(chunkSize)
            .rowMapper { rs, _ ->
                ProductSignalSummary(
                    productId = rs.getLong("product_id"),
                    viewCount = rs.getLong("view_count"),
                    likeCount = rs.getLong("like_count"),
                    orderQuantity = rs.getLong("order_quantity"),
                )
            }
            .build()
    }

    @Bean
    @StepScope
    fun weeklyPeriodProcessor(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): ItemProcessor<ProductSignalSummary, ProductPeriodMetrics> {
        val period = weeklyPeriodOf(targetDate)
        return ItemProcessor { summary ->
            ProductPeriodMetrics.of(
                productId = summary.productId,
                periodKey = period.key,
                viewCount = summary.viewCount,
                likeCount = summary.likeCount,
                orderQuantity = summary.orderQuantity,
            )
        }
    }

    @Bean
    fun weeklyMetricsWriter(): JdbcBatchItemWriter<ProductPeriodMetrics> = JdbcBatchItemWriterBuilder<ProductPeriodMetrics>()
        .dataSource(dataSource)
        .sql(
            "INSERT INTO product_metrics_weekly " +
                "(product_id, period_key, view_count, like_count, order_quantity, created_at, updated_at) " +
                "VALUES (:productId, :periodKey, :viewCount, :likeCount, :orderQuantity, NOW(6), NOW(6))",
        )
        .beanMapped()
        .build()

    private fun weeklyPeriodOf(targetDate: String?): RankingPeriod =
        RankingPeriod.weeklyOf(LocalDate.parse(requireNotNull(targetDate) { "targetDate 잡 파라미터가 필요하다" }))
}
