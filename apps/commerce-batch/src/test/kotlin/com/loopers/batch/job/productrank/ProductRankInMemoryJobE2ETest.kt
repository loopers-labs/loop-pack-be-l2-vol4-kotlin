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
        "spring.batch.job.name=${ProductRankInMemoryJobConfig.JOB_NAME}",
        "spring.batch.job.enabled=false",
    ],
)
class ProductRankInMemoryJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(ProductRankInMemoryJobConfig.JOB_NAME) private val job: Job,
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
    private fun weeklyParams() = JobParametersBuilder()
        .addString("period", "WEEKLY")
        .addString("targetDate", "2026-07-20")
        .addString("runTag", UUID.randomUUID().toString())
        .toJobParameters()

    @DisplayName("인메모리 집계로 raw 행이 상품별 합산되어, 주간 랭킹이 MV에 저장된다 (staging 미사용).")
    @Test
    fun aggregatesInMemory_andConfirmsRanking() {
        jobLauncherTestUtils.job = job
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 13), 5) // 50
        seedMetric(1L, "LIKE", LocalDate.of(2026, 7, 17), 1) // +50 → 100
        seedMetric(2L, "SALES", LocalDate.of(2026, 7, 15), 3) // 1500

        val execution = jobLauncherTestUtils.launchJob(weeklyParams())

        assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        val rows = jdbcTemplate.queryForList(
            "SELECT rank_no, product_id, score FROM mv_product_rank_weekly WHERE aggregated_date = '2026-07-13' ORDER BY rank_no",
        )
        assertThat(rows).hasSize(2)
        assertThat(rows[0]["product_id"]).isEqualTo(2L)
        assertThat(rows[0]["score"]).isEqualTo(1500L)
        assertThat(rows[1]["product_id"]).isEqualTo(1L)
        assertThat(rows[1]["score"]).isEqualTo(100L)
        // staging은 이 변형에서 사용되지 않는다
        val stagingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_rank_staging", Long::class.java)
        assertThat(stagingCount).isEqualTo(0L)
    }

    @DisplayName("같은 창으로 두 번 실행해도, 집계 상태가 Job 실행마다 새로 만들어져 점수가 두 배로 불지 않는다 (@JobScope).")
    @Test
    fun doesNotDoubleScores_whenExecutedTwice() {
        jobLauncherTestUtils.job = job
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 14), 7) // 70

        val first = jobLauncherTestUtils.launchJob(weeklyParams())
        val second = jobLauncherTestUtils.launchJob(weeklyParams())

        assertThat(first.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        assertThat(second.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        val rows = jdbcTemplate.queryForList(
            "SELECT score FROM mv_product_rank_weekly WHERE aggregated_date = '2026-07-13' AND product_id = 1",
        )
        assertThat(rows).hasSize(1) // 스냅샷 교체 — 중복 없음
        assertThat(rows[0]["score"]).isEqualTo(70L) // 두 배(140)가 아니어야 한다
    }
}
