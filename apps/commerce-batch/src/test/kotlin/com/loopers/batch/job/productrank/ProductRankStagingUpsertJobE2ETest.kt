package com.loopers.batch.job.productrank

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
@TestPropertySource(
    properties = [
        "spring.batch.job.name=${ProductRankStagingUpsertJobConfig.JOB_NAME}",
        "spring.batch.job.enabled=false",
    ],
)
class ProductRankStagingUpsertJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(ProductRankStagingUpsertJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seedMetric(productId: Long, type: String, metricDate: LocalDate, count: Long) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, type, metric_date, count, updated_at) VALUES (?, ?, ?, ?, NOW(6))",
            productId,
            type,
            metricDate,
            count,
        )
    }

    // BATCH_* 메타데이터는 DatabaseCleanUp 대상이 아니라 테스트 간 남는다 — runTag로 JobInstance를 항상 새로 만든다
    private fun params(period: String, targetDate: String) = JobParametersBuilder()
        .addString("period", period)
        .addString("targetDate", targetDate)
        .addString("runTag", UUID.randomUUID().toString())
        .toJobParameters()

    @DisplayName("여러 날짜·타입의 raw 행이 상품별로 upsert 누적되어, 주간 랭킹이 MV에 저장된다.")
    @Test
    fun accumulatesRawRowsAcrossDaysAndTypes() {
        jobLauncherTestUtils.job = job
        // 집계 창: 2026-07-13 ~ 07-19. 같은 상품의 행이 날짜×타입으로 흩어져 있어도 합산돼야 한다.
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 13), 5) // 50
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 14), 5) // +50
        seedMetric(1L, "LIKE", LocalDate.of(2026, 7, 15), 4) // +200 → 300
        seedMetric(2L, "SALES", LocalDate.of(2026, 7, 19), 1) // 500
        seedMetric(2L, "SALES", LocalDate.of(2026, 7, 20), 9) // 창 밖(다음 주 월) — 제외

        val execution = jobLauncherTestUtils.launchJob(params("WEEKLY", "2026-07-20"))

        assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        val rows = jdbcTemplate.queryForList(
            "SELECT rank_no, product_id, score FROM mv_product_rank_weekly WHERE aggregated_date = '2026-07-13' ORDER BY rank_no",
        )
        assertThat(rows).hasSize(2)
        assertThat(rows[0]["product_id"]).isEqualTo(2L)
        assertThat(rows[0]["score"]).isEqualTo(500L)
        assertThat(rows[1]["product_id"]).isEqualTo(1L)
        assertThat(rows[1]["score"]).isEqualTo(300L)
    }

    @DisplayName("MONTHLY 기간이면 지난달 전체가 집계되어 mv_product_rank_monthly에 저장된다.")
    @Test
    fun aggregatesLastMonth_whenPeriodIsMonthly() {
        jobLauncherTestUtils.job = job
        seedMetric(1L, "VIEW", LocalDate.of(2026, 6, 1), 1) // 지난달 1일 (포함)
        seedMetric(1L, "VIEW", LocalDate.of(2026, 6, 30), 1) // 지난달 말일 (포함)
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 1), 100) // 이번 달 (제외)

        val execution = jobLauncherTestUtils.launchJob(params("MONTHLY", "2026-07-20"))

        assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        val row = jdbcTemplate.queryForMap(
            "SELECT rank_no, score FROM mv_product_rank_monthly WHERE aggregated_date = '2026-06-01' AND product_id = 1",
        )
        assertThat(row["rank_no"]).isEqualTo(1)
        assertThat(row["score"]).isEqualTo(20L) // 2 × 10
    }

    @DisplayName("이전 실행의 staging 잔여 데이터가 있어도, clear Step이 비우고 시작해 결과가 오염되지 않는다.")
    @Test
    fun clearsStaleStagingBeforeAggregation() {
        jobLauncherTestUtils.job = job
        jdbcTemplate.update("INSERT INTO product_rank_staging (product_id, score) VALUES (99, 999999)")
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 14), 1)

        val execution = jobLauncherTestUtils.launchJob(params("WEEKLY", "2026-07-20"))

        assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        val productIds = jdbcTemplate.queryForList(
            "SELECT product_id FROM mv_product_rank_weekly WHERE aggregated_date = '2026-07-13'",
            Long::class.java,
        )
        assertThat(productIds).containsExactly(1L) // 잔여 상품 99 미포함
    }
}
