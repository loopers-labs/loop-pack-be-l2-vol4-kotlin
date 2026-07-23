package com.loopers.batch.job.productrank

import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.metrics.ProductPeriodMetrics
import com.loopers.domain.metrics.ProductSignalSummary
import com.loopers.domain.ranking.RankingPeriod
import org.springframework.batch.core.Step
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 기간(주/월) 집계 스텝의 구성 요소를 만든다 — 주간·월간은 대상 테이블과 기간 계산만 다르고 나머지 구성이 같다.
 * 정리 tasklet, 시간별 집계 페이징 리더, 기간 키 부여 프로세서, 배치 insert 라이터를 조립한다.
 */
class PeriodAggregateStepFactory(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSource: DataSource,
    private val chunkSize: Int,
    private val table: String,
    private val periodResolver: (LocalDate) -> RankingPeriod,
    private val stepMonitorListener: StepMonitorListener,
    private val chunkListener: ChunkListener,
) {
    fun resolvePeriod(targetDate: String?): RankingPeriod =
        periodResolver(LocalDate.parse(requireNotNull(targetDate) { "targetDate 잡 파라미터가 필요하다" }))

    fun cleanTasklet(targetDate: String?): Tasklet {
        val period = resolvePeriod(targetDate)
        return Tasklet { _, _ ->
            JdbcTemplate(dataSource).update("DELETE FROM $table WHERE period_key = ?", period.key)
            RepeatStatus.FINISHED
        }
    }

    fun taskletStep(name: String, tasklet: Tasklet): Step = StepBuilder(name, jobRepository)
        .tasklet(tasklet, transactionManager)
        .listener(stepMonitorListener)
        .build()

    fun aggregateStep(
        name: String,
        reader: JdbcPagingItemReader<ProductSignalSummary>,
        processor: ItemProcessor<ProductSignalSummary, ProductPeriodMetrics>,
        writer: JdbcBatchItemWriter<ProductPeriodMetrics>,
    ): Step = StepBuilder(name, jobRepository)
        .chunk<ProductSignalSummary, ProductPeriodMetrics>(chunkSize, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .listener(stepMonitorListener)
        .listener(chunkListener as Any)
        .build()

    fun hourlyReader(name: String, targetDate: String?): JdbcPagingItemReader<ProductSignalSummary> {
        val period = resolvePeriod(targetDate)
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
            .name(name)
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

    fun periodProcessor(targetDate: String?): ItemProcessor<ProductSignalSummary, ProductPeriodMetrics> {
        val period = resolvePeriod(targetDate)
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

    fun metricsWriter(): JdbcBatchItemWriter<ProductPeriodMetrics> = JdbcBatchItemWriterBuilder<ProductPeriodMetrics>()
        .dataSource(dataSource)
        .sql(
            "INSERT INTO $table " +
                "(product_id, period_key, view_count, like_count, order_quantity, created_at, updated_at) " +
                "VALUES (:productId, :periodKey, :viewCount, :likeCount, :orderQuantity, NOW(6), NOW(6))",
        )
        .beanMapped()
        .build()
}
