package com.loopers.batch.job.productrank

import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

// 테스트 전용 잡 없이 실제 productRankJob 빈을 그대로 실행한다.
@SpringBatchTest
@SpringBootTest(
    properties = [
        "spring.batch.job.name=productRankJob",
        "spring.batch.job.enabled=false",
        "loopers.batch.product-rank.chunk-size=2",
    ],
)
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class ProductRankJobIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    // JobExecution 을 반환하면 @SpringBatchTest 의 스코프 리스너가 팩토리 메서드로 오인해 무인자 호출을 시도한다 — 상태만 반환한다.
    private fun launch(targetDate: String = "2026-07-21"): BatchStatus = jobLauncherTestUtils.launchJob(params(targetDate)).status

    private fun params(targetDate: String): JobParameters = JobParametersBuilder()
        .addString("targetDate", targetDate)
        .addString("uniqueRunId", UUID.randomUUID().toString())
        .toJobParameters()

    private fun seedHourly(productId: Long, statHour: LocalDateTime, view: Long = 0, like: Long = 0, order: Long = 0) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_hourly (product_id, stat_hour, view_count, like_count, order_quantity, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))",
            productId,
            statHour,
            view,
            like,
            order,
        )
    }

    private fun rows(table: String): List<Map<String, Any>> =
        jdbcTemplate.queryForList("SELECT product_id, period_key, view_count, like_count, order_quantity FROM $table ORDER BY product_id")

    private fun Map<String, Any>.long(column: String): Long = (getValue(column) as Number).toLong()

    @DisplayName("productRankJob 을 실행하면,")
    @Nested
    inner class Run {
        @Test
        fun `targetDate 하나로 주간·월간 집계가 각자의 창으로 함께 적재된다`() {
            // targetDate=2026-07-21 → 주 창 [7/20, 7/27), 월 창 [7/1, 8/1)
            seedHourly(101L, LocalDateTime.of(2026, 7, 21, 10, 0), view = 2, like = 1, order = 3)
            // 주 창 밖, 월 창 안
            seedHourly(101L, LocalDateTime.of(2026, 7, 3, 10, 0), view = 5)
            // 두 창 모두 밖(6월)
            seedHourly(101L, LocalDateTime.of(2026, 6, 30, 10, 0), view = 100)

            val status = launch()

            assertThat(status).isEqualTo(BatchStatus.COMPLETED)
            val weekly = rows("product_metrics_weekly")
            assertThat(weekly).hasSize(1)
            with(weekly.single()) {
                assertThat(get("period_key")).isEqualTo("2026W30")
                assertThat(long("view_count")).isEqualTo(2L)
                assertThat(long("like_count")).isEqualTo(1L)
                assertThat(long("order_quantity")).isEqualTo(3L)
            }
            val monthly = rows("product_metrics_monthly")
            assertThat(monthly).hasSize(1)
            with(monthly.single()) {
                assertThat(get("period_key")).isEqualTo("202607")
                assertThat(long("view_count")).isEqualTo(7L)
                assertThat(long("like_count")).isEqualTo(1L)
                assertThat(long("order_quantity")).isEqualTo(3L)
            }
        }

        @Test
        fun `같은 targetDate 로 다시 실행해도 두 테이블 모두 결과가 같다`() {
            seedHourly(101L, LocalDateTime.of(2026, 7, 21, 10, 0), view = 3)

            launch()
            val second = launch()

            assertThat(second).isEqualTo(BatchStatus.COMPLETED)
            assertThat(rows("product_metrics_weekly")).hasSize(1)
            assertThat(rows("product_metrics_monthly")).hasSize(1)
            assertThat(rows("product_metrics_weekly").single().long("view_count")).isEqualTo(3L)
            assertThat(rows("product_metrics_monthly").single().long("view_count")).isEqualTo(3L)
        }
    }
}
