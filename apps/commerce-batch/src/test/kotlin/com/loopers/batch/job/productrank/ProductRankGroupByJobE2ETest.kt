package com.loopers.batch.job.productrank

import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.batch.core.JobParametersInvalidException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
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
        "spring.batch.job.name=${ProductRankGroupByJobConfig.JOB_NAME}",
        // 부팅 시 자동 실행 방지 — 파라미터 없는 startup 런이 validator에 걸려 컨텍스트가 죽는다
        "spring.batch.job.enabled=false",
    ],
)
class ProductRankGroupByJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(ProductRankGroupByJobConfig.JOB_NAME) private val job: Job,
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
    private fun weeklyParams(): JobParameters = JobParametersBuilder()
        .addString("period", "WEEKLY")
        .addString("targetDate", "2026-07-20")
        .addString("runTag", UUID.randomUUID().toString())
        .toJobParameters()

    @DisplayName("주간 기간의 메트릭만 가중치 집계되어, TOP 랭킹이 MV에 저장된다.")
    @Test
    fun aggregatesWeeklyWindowOnly_andConfirmsRanking() {
        jobLauncherTestUtils.job = job
        // 집계 창: 2026-07-13(월) ~ 07-19(일). 기본 가중치 VIEW=10, LIKE=50, SALES=500.
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 13), 10) // 100
        seedMetric(1L, "LIKE", LocalDate.of(2026, 7, 19), 2) // +100 → 200
        seedMetric(2L, "SALES", LocalDate.of(2026, 7, 15), 2) // 1000
        seedMetric(3L, "VIEW", LocalDate.of(2026, 7, 16), 1) // 10
        seedMetric(3L, "SALES", LocalDate.of(2026, 7, 12), 100) // 창 밖 — 제외되어야 함

        val execution = jobLauncherTestUtils.launchJob(weeklyParams())

        assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        val rows = jdbcTemplate.queryForList(
            "SELECT rank_no, product_id, score FROM mv_product_rank_weekly WHERE aggregated_date = '2026-07-13' ORDER BY rank_no",
        )
        assertThat(rows).hasSize(3)
        assertThat(rows[0]["product_id"]).isEqualTo(2L)
        assertThat(rows[0]["score"]).isEqualTo(1000L)
        assertThat(rows[1]["product_id"]).isEqualTo(1L)
        assertThat(rows[1]["score"]).isEqualTo(200L)
        assertThat(rows[2]["product_id"]).isEqualTo(3L)
        assertThat(rows[2]["score"]).isEqualTo(10L) // 창 밖 SALES 미반영
    }

    @DisplayName("활성 가중치 버전이 있으면, 그 가중치로 점수를 계산한다.")
    @Test
    fun usesActiveWeights_whenActiveVersionExists() {
        jobLauncherTestUtils.job = job
        jdbcTemplate.update(
            "INSERT INTO ranking_weight_config (version, view_weight, like_weight, order_weight, status, created_at) " +
                "VALUES ('v2', 20, 100, 1000, 'ACTIVE', NOW(6))",
        )
        seedMetric(1L, "VIEW", LocalDate.of(2026, 7, 14), 3)

        val execution = jobLauncherTestUtils.launchJob(weeklyParams())

        assertThat(execution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode)
        val score = jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_weekly WHERE aggregated_date = '2026-07-13' AND product_id = 1",
            Long::class.java,
        )
        assertThat(score).isEqualTo(60L) // 3 × 20
    }

    @DisplayName("period 파라미터가 없으면, 어떤 Step도 실행되기 전에 검증 예외로 거부된다.")
    @Test
    fun rejectsLaunch_whenPeriodMissing() {
        jobLauncherTestUtils.job = job

        val stepExecutionsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BATCH_STEP_EXECUTION", Long::class.java)

        assertThatThrownBy { jobLauncherTestUtils.launchJob(JobParametersBuilder().toJobParameters()) }
            .isInstanceOf(JobParametersInvalidException::class.java)

        // staging TRUNCATE 등 어떤 Step도 새로 실행되지 않았어야 한다
        val stepExecutionsAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BATCH_STEP_EXECUTION", Long::class.java)
        assertThat(stepExecutionsAfter).isEqualTo(stepExecutionsBefore)
    }
}
