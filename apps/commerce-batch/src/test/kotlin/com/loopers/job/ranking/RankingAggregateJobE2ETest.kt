package com.loopers.job.ranking

import com.loopers.batch.job.ranking.RankingAggregateJobConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${RankingAggregateJobConfig.JOB_NAME}"])
class RankingAggregateJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(RankingAggregateJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val targetDate = LocalDate.of(2025, 7, 3)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0")
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS daily_product_ranking_metrics (
                product_id BIGINT NOT NULL,
                metric_date DATE NOT NULL,
                view_count BIGINT NOT NULL DEFAULT 0,
                like_count BIGINT NOT NULL DEFAULT 0,
                order_count BIGINT NOT NULL DEFAULT 0,
                sales_amount BIGINT NOT NULL DEFAULT 0,
                ranking_score DOUBLE NOT NULL DEFAULT 0.0,
                created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                updated_at DATETIME(6) NOT NULL DEFAULT NOW(6),
                PRIMARY KEY (product_id, metric_date)
            )
            """,
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
                product_id BIGINT NOT NULL,
                period_start DATE NOT NULL,
                ranking_score DOUBLE NOT NULL DEFAULT 0.0,
                `rank` INT NOT NULL DEFAULT 0,
                PRIMARY KEY (product_id, period_start),
                INDEX idx_weekly_period_rank (period_start, `rank`)
            )
            """,
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
                product_id BIGINT NOT NULL,
                period_start DATE NOT NULL,
                ranking_score DOUBLE NOT NULL DEFAULT 0.0,
                `rank` INT NOT NULL DEFAULT 0,
                PRIMARY KEY (product_id, period_start),
                INDEX idx_monthly_period_rank (period_start, `rank`)
            )
            """,
        )
        jdbcTemplate.execute("TRUNCATE TABLE daily_product_ranking_metrics")
        jdbcTemplate.execute("TRUNCATE TABLE mv_product_rank_weekly")
        jdbcTemplate.execute("TRUNCATE TABLE mv_product_rank_monthly")
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1")
    }

    @DisplayName("일별 메트릭을 읽어 주간/월간 MV에 집계하고 rank를 갱신한다")
    @Test
    fun shouldAggregateAndRank() {
        // arrange
        jobLauncherTestUtils.job = job
        insertDailyMetric(productId = 1, date = targetDate, rankingScore = 30.0)
        insertDailyMetric(productId = 2, date = targetDate, rankingScore = 50.0)
        insertDailyMetric(productId = 3, date = targetDate, rankingScore = 10.0)

        // act
        val jobParameters = JobParametersBuilder()
            .addLocalDate("requestDate", targetDate)
            .addString("runId", UUID.randomUUID().toString())
            .toJobParameters()
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters)

        // assert
        assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)

        val weeklyRows = jdbcTemplate.queryForList(
            "SELECT product_id, ranking_score, `rank` FROM mv_product_rank_weekly WHERE period_start = '2025-06-30' ORDER BY `rank`",
        )
        assertAll(
            { assertThat(weeklyRows).hasSize(3) },
            { assertThat(weeklyRows[0]["product_id"]).isEqualTo(2L) },
            { assertThat(weeklyRows[0]["rank"]).isEqualTo(1) },
            { assertThat(weeklyRows[1]["product_id"]).isEqualTo(1L) },
            { assertThat(weeklyRows[1]["rank"]).isEqualTo(2) },
            { assertThat(weeklyRows[2]["product_id"]).isEqualTo(3L) },
            { assertThat(weeklyRows[2]["rank"]).isEqualTo(3) },
        )

        val monthlyRows = jdbcTemplate.queryForList(
            "SELECT product_id, ranking_score, `rank` FROM mv_product_rank_monthly WHERE period_start = '2025-07-01' ORDER BY `rank`",
        )
        assertAll(
            { assertThat(monthlyRows).hasSize(3) },
            { assertThat(monthlyRows[0]["product_id"]).isEqualTo(2L) },
            { assertThat(monthlyRows[0]["rank"]).isEqualTo(1) },
        )
    }

    @DisplayName("같은 기간에 여러 날짜의 메트릭이 있으면 SUM으로 집계된다")
    @Test
    fun shouldAggregateMultipleDays() {
        // arrange
        jobLauncherTestUtils.job = job
        val requestDate = LocalDate.of(2025, 7, 2)
        insertDailyMetric(productId = 1, date = LocalDate.of(2025, 7, 1), rankingScore = 20.0)
        insertDailyMetric(productId = 1, date = LocalDate.of(2025, 7, 2), rankingScore = 30.0)

        // act
        val params = JobParametersBuilder()
            .addLocalDate("requestDate", requestDate)
            .addString("runId", UUID.randomUUID().toString())
            .toJobParameters()
        jobLauncherTestUtils.launchJob(params)

        // assert - weekly: 20 + 30 = 50
        val score = jdbcTemplate.queryForObject(
            "SELECT ranking_score FROM mv_product_rank_weekly WHERE product_id = 1 AND period_start = '2025-06-30'",
            Double::class.java,
        )
        assertThat(score).isEqualTo(50.0)
    }

    @DisplayName("같은 날짜로 재실행해도 점수가 이중 누적되지 않는다")
    @Test
    fun shouldBeIdempotent() {
        // arrange
        jobLauncherTestUtils.job = job
        insertDailyMetric(productId = 1, date = targetDate, rankingScore = 10.0)

        // act - 같은 날짜로 두 번 실행
        repeat(2) {
            val params = JobParametersBuilder()
                .addLocalDate("requestDate", targetDate)
                .addString("runId", UUID.randomUUID().toString())
                .toJobParameters()
            jobLauncherTestUtils.launchJob(params)
        }

        // assert - 10.0이어야 하며 20.0이 되면 안 된다
        val score = jdbcTemplate.queryForObject(
            "SELECT ranking_score FROM mv_product_rank_weekly WHERE product_id = 1 AND period_start = '2025-06-30'",
            Double::class.java,
        )
        assertThat(score).isEqualTo(10.0)
    }

    private fun insertDailyMetric(productId: Long, date: LocalDate, rankingScore: Double) {
        jdbcTemplate.update(
            """
            INSERT INTO daily_product_ranking_metrics (product_id, metric_date, view_count, like_count, order_count, sales_amount, ranking_score, created_at, updated_at)
            VALUES (?, ?, 0, 0, 0, 0, ?, NOW(6), NOW(6))
            """,
            productId,
            date,
            rankingScore,
        )
    }
}
