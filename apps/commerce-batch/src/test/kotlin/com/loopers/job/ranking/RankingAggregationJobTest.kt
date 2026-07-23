package com.loopers.job.ranking

import com.loopers.batch.job.ranking.RankingJobConfig
import com.loopers.domain.ranking.PeriodType
import com.loopers.infrastructure.ranking.ProductRankRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import java.util.UUID
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${RankingJobConfig.JOB_NAME}"])
class RankingAggregationJobTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(RankingJobConfig.JOB_NAME) private val job: Job,
    private val productRankRepository: ProductRankRepository,
    private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        jdbcTemplate.execute("DELETE FROM mv_product_rank")
        jdbcTemplate.execute("DELETE FROM product_metrics")
    }

    @DisplayName("필수 파라미터 누락 시 배치가 실패한다")
    @Test
    fun shouldFail_whenParameterMissing() {
        val jobExecution = jobLauncherTestUtils.launchJob()

        assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.FAILED.exitCode)
    }

    @DisplayName("product_metrics 데이터를 기반으로 주간 TOP 100 랭킹을 집계한다")
    @Test
    fun shouldAggregateWeeklyRanking() {
        // arrange
        insertMetrics(productId = 1L, viewCount = 1000, likeCount = 50, orderCount = 10, salesAmount = 500_000)
        insertMetrics(productId = 2L, viewCount = 500, likeCount = 100, orderCount = 20, salesAmount = 1_000_000)
        insertMetrics(productId = 3L, viewCount = 200, likeCount = 10, orderCount = 5, salesAmount = 100_000)

        val jobParameters = JobParametersBuilder()
            .addString("periodType", "WEEKLY")
            .addString("periodKey", "2026-W30")
            .addString("run.id", UUID.randomUUID().toString())
            .toJobParameters()

        // act
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // assert
        assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

        val rankings = productRankRepository.findByPeriodTypeAndPeriodKeyOrderByRankingAsc(
            PeriodType.WEEKLY,
            "2026-W30",
        )
        assertThat(rankings).hasSize(3)
        assertThat(rankings[0].ranking).isEqualTo(1)
        assertThat(rankings[0].productId).isEqualTo(2L)
        assertThat(rankings[1].productId).isEqualTo(1L)
        assertThat(rankings[2].productId).isEqualTo(3L)
    }

    @DisplayName("월간 집계도 정상 동작한다")
    @Test
    fun shouldAggregateMonthlyRanking() {
        // arrange
        insertMetrics(productId = 10L, viewCount = 3000, likeCount = 200, orderCount = 50, salesAmount = 5_000_000)
        insertMetrics(productId = 11L, viewCount = 100, likeCount = 5, orderCount = 1, salesAmount = 10_000)

        val jobParameters = JobParametersBuilder()
            .addString("periodType", "MONTHLY")
            .addString("periodKey", "2026-07")
            .addString("run.id", UUID.randomUUID().toString())
            .toJobParameters()

        // act
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // assert
        assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

        val rankings = productRankRepository.findByPeriodTypeAndPeriodKeyOrderByRankingAsc(
            PeriodType.MONTHLY,
            "2026-07",
        )
        assertThat(rankings).hasSize(2)
        assertThat(rankings[0].productId).isEqualTo(10L)
    }

    @DisplayName("TOP 100을 초과하는 데이터는 잘린다")
    @Test
    fun shouldLimitTo100() {
        // arrange: 120개 상품 삽입
        for (i in 1L..120L) {
            insertMetrics(productId = i, viewCount = i * 10, likeCount = i, orderCount = i, salesAmount = i * 1000)
        }

        val jobParameters = JobParametersBuilder()
            .addString("periodType", "WEEKLY")
            .addString("periodKey", "2026-W30")
            .addString("run.id", UUID.randomUUID().toString())
            .toJobParameters()

        // act
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // assert
        assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

        val rankings = productRankRepository.findByPeriodTypeAndPeriodKeyOrderByRankingAsc(
            PeriodType.WEEKLY,
            "2026-W30",
        )
        assertThat(rankings).hasSize(100)
        assertThat(rankings.first().ranking).isEqualTo(1)
        assertThat(rankings.last().ranking).isEqualTo(100)
    }

    private fun insertMetrics(productId: Long, viewCount: Long, likeCount: Long, orderCount: Long, salesAmount: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO product_metrics (product_id, view_count, like_count, order_count, sales_amount, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
            productId,
            viewCount,
            likeCount,
            orderCount,
            salesAmount,
        )
    }
}
