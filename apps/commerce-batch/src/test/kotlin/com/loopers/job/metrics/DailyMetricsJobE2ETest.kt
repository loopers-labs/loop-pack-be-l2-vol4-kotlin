package com.loopers.job.metrics

import com.loopers.batch.job.metrics.DailyMetricsJobConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${DailyMetricsJobConfig.JOB_NAME}"])
class DailyMetricsJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(DailyMetricsJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
) {
    private val baseDate = LocalDate.of(2026, 7, 20)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS product_metrics")
        jdbcTemplate.execute(
            "CREATE TABLE product_metrics (product_id BIGINT PRIMARY KEY, like_count INT NOT NULL, " +
                "sales_count INT NOT NULL, view_count INT NOT NULL, version BIGINT NOT NULL DEFAULT 0)",
        )
        jdbcTemplate.update("DELETE FROM product_metrics_daily")
        jdbcTemplate.update("DELETE FROM product_metrics_snapshot")

        // 현재 누적
        insertCumulative(1, like = 10, sales = 5, view = 100)
        insertCumulative(2, like = 3, sales = 0, view = 20)
        insertCumulative(3, like = 5, sales = 5, view = 5)
        // 직전 스냅샷 (2번은 스냅샷 없음 = 신규, 3번은 변화 없음)
        insertSnapshot(1, like = 7, sales = 2, view = 80)
        insertSnapshot(3, like = 5, sales = 5, view = 5)
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM product_metrics_daily")
        jdbcTemplate.update("DELETE FROM product_metrics_snapshot")
        jdbcTemplate.execute("DROP TABLE IF EXISTS product_metrics")
    }

    @DisplayName("현재 누적 − 직전 스냅샷 = 당일 델타를 product_metrics_daily 에 적재하고 스냅샷을 전진시킨다 (변화 없는 상품 제외)")
    @Test
    fun capturesDailyDeltaAndAdvancesSnapshot() {
        val result = launchDailyJob()

        // p1: 10-7=3, 5-2=3, 100-80=20 / p2(신규): 3,0,20 / p3: 0,0,0 → 제외
        assertAll(
            { assertThat(result.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(dailyCount()).isEqualTo(2L) },
            { assertThat(daily(1, "like_count")).isEqualTo(3) },
            { assertThat(daily(1, "sales_count")).isEqualTo(3) },
            { assertThat(daily(1, "view_count")).isEqualTo(20) },
            { assertThat(daily(2, "like_count")).isEqualTo(3) },
            { assertThat(daily(2, "view_count")).isEqualTo(20) },
            { assertThat(dailyExists(3)).isFalse() },
            // 스냅샷이 현재 누적으로 전진
            { assertThat(snapshot(1, "view_count")).isEqualTo(100) },
            { assertThat(snapshot(2, "like_count")).isEqualTo(3) },
        )
    }

    @DisplayName("이벤트 없이 재실행하면 델타가 0이라 아무것도 바꾸지 않는다 (멱등)")
    @Test
    fun rerunWithoutNewEventsIsIdempotent() {
        launchDailyJob()
        launchDailyJob()

        assertAll(
            { assertThat(dailyCount()).isEqualTo(2L) },
            { assertThat(daily(1, "like_count")).isEqualTo(3) },
            { assertThat(daily(2, "view_count")).isEqualTo(20) },
        )
    }

    private fun launchDailyJob(): DailyRunResult {
        jobLauncherTestUtils.job = job
        val params = JobParametersBuilder()
            .addString("baseDate", baseDate.toString())
            .addLong("testRunId", System.nanoTime())
            .toJobParameters()
        val execution = jobLauncherTestUtils.launchJob(params)
        return DailyRunResult(execution.exitStatus.exitCode)
    }

    private data class DailyRunResult(val exitCode: String)

    private fun insertCumulative(productId: Long, like: Int, sales: Int, view: Int) =
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, version) VALUES (?, ?, ?, ?, 0)",
            productId,
            like,
            sales,
            view,
        )

    private fun insertSnapshot(productId: Long, like: Int, sales: Int, view: Int) =
        jdbcTemplate.update(
            "INSERT INTO product_metrics_snapshot (product_id, like_count, sales_count, view_count) VALUES (?, ?, ?, ?)",
            productId,
            like,
            sales,
            view,
        )

    private fun dailyCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_metrics_daily WHERE metric_date = ?",
            Long::class.java,
            baseDate,
        )!!

    private fun daily(productId: Long, column: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT $column FROM product_metrics_daily WHERE product_id = ? AND metric_date = ?",
            Int::class.java,
            productId,
            baseDate,
        )!!

    private fun dailyExists(productId: Long): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_metrics_daily WHERE product_id = ? AND metric_date = ?",
            Int::class.java, productId, baseDate,
        )!! > 0

    private fun snapshot(productId: Long, column: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT $column FROM product_metrics_snapshot WHERE product_id = ?",
            Int::class.java,
            productId,
        )!!
}
