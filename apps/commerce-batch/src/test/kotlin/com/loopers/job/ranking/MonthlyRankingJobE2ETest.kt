package com.loopers.job.ranking

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig
import com.loopers.config.redis.RedisConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
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
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${MonthlyRankingJobConfig.JOB_NAME}"])
class MonthlyRankingJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(MonthlyRankingJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    @param:Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    // baseDate 2026-07-22 → 월 2026-07 (07-01 ~ 07-31)
    private val baseDate = LocalDate.of(2026, 7, 22)
    private val yearMonth = "2026-07"
    private val zsetKey = "ranking:monthly:$yearMonth"

    @BeforeEach
    fun setUp() {
        cleanTables()
        insertDaily(1, "2026-07-01", like = 1, sales = 2, view = 10)
        insertDaily(1, "2026-07-15", like = 0, sales = 3, view = 5)
        insertDaily(1, "2026-07-31", like = 2, sales = 0, view = 0) // 월 마지막 날
        insertDaily(2, "2026-07-10", like = 5, sales = 0, view = 0)
        insertDaily(1, "2026-06-30", like = 99, sales = 99, view = 99) // 전월(6월) 제외
        insertDaily(2, "2026-08-01", like = 99, sales = 99, view = 99) // 차월(8월) 제외
    }

    @AfterEach
    fun tearDown() {
        cleanTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("일간 집계를 한 달(1일~말일) 범위로 합산해 MV 테이블과 Redis ZSET에 적재한다 (월 경계 밖 제외)")
    @Test
    fun aggregatesMonthlyRankingWithinCalendarMonth() {
        val result = launchMonthlyJob()

        // product1: like=1+0+2=3, sales=2+3+0=5, view=10+5+0=15 → 15*0.1+3*0.2+5*0.7 = 5.6
        // product2: like=5, sales=0, view=0                       → 5*0.2 = 1.0
        assertAll(
            { assertThat(result.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(monthlyCount()).isEqualTo(2L) },
            { assertThat(monthlyColumn(1, "like_count")).isEqualTo(3) },
            { assertThat(monthlyColumn(1, "sales_count")).isEqualTo(5) },
            { assertThat(monthlyColumn(1, "view_count")).isEqualTo(15) },
            { assertThat(monthlyScore(1)).isCloseTo(5.6, within(1e-9)) },
            { assertThat(monthlyColumn(2, "like_count")).isEqualTo(5) },
            { assertThat(monthlyScore(2)).isCloseTo(1.0, within(1e-9)) },
            { assertThat(zsetScore(1)).isCloseTo(5.6, within(1e-9)) },
            { assertThat(zsetScore(2)).isCloseTo(1.0, within(1e-9)) },
            { assertThat(zsetOrderDesc()).containsExactly("1", "2") },
        )
    }

    @DisplayName("beforeStep에서 기존 월간 ZSET을 비우고 재적재해, 이전 실행에 남은 상품이 정리된다")
    @Test
    fun beforeStepClearsStaleZsetMembers() {
        redisTemplate.opsForZSet().add(zsetKey, "999", 999.0)

        launchMonthlyJob()

        assertAll(
            { assertThat(zsetScore(999)).isNull() },
            { assertThat(zsetOrderDesc()).containsExactly("1", "2") },
        )
    }

    private fun launchMonthlyJob(): MonthlyRunResult {
        jobLauncherTestUtils.job = job
        val params = JobParametersBuilder()
            .addString("baseDate", baseDate.toString())
            .addLong("testRunId", System.nanoTime())
            .toJobParameters()
        val execution = jobLauncherTestUtils.launchJob(params)
        return MonthlyRunResult(execution.exitStatus.exitCode)
    }

    private data class MonthlyRunResult(val exitCode: String)

    private fun insertDaily(productId: Long, date: String, like: Int, sales: Int, view: Int) =
        jdbcTemplate.update(
            "INSERT INTO product_metrics_daily (product_id, metric_date, like_count, sales_count, view_count) VALUES (?, ?, ?, ?, ?)",
            productId,
            date,
            like,
            sales,
            view,
        )

    private fun cleanTables() {
        jdbcTemplate.update("DELETE FROM product_metrics_daily")
        jdbcTemplate.update("DELETE FROM product_metrics_monthly")
    }

    private fun monthlyCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_metrics_monthly WHERE month_key = ?",
            Long::class.java,
            yearMonth,
        )!!

    private fun monthlyColumn(productId: Long, column: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT $column FROM product_metrics_monthly WHERE product_id = ? AND month_key = ?",
            Int::class.java,
            productId,
            yearMonth,
        )!!

    private fun monthlyScore(productId: Long): Double =
        jdbcTemplate.queryForObject(
            "SELECT score FROM product_metrics_monthly WHERE product_id = ? AND month_key = ?",
            Double::class.java,
            productId,
            yearMonth,
        )!!

    private fun zsetScore(productId: Long): Double? =
        redisTemplate.opsForZSet().score(zsetKey, productId.toString())

    private fun zsetOrderDesc(): List<String> =
        redisTemplate.opsForZSet().reverseRange(zsetKey, 0, -1)?.toList() ?: emptyList()
}
