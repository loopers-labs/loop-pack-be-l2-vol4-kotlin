package com.loopers.job.ranking

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
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
@TestPropertySource(properties = ["spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}"])
class WeeklyRankingJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(WeeklyRankingJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    @param:Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val baseDate = LocalDate.of(2026, 7, 22)
    private val yearWeek = "2026-W30"
    private val zsetKey = "ranking:weekly:$yearWeek"

    @BeforeEach
    fun setUp() {
        cleanTables()
        insertDaily(1, "2026-07-20", like = 1, sales = 2, view = 10)
        insertDaily(1, "2026-07-22", like = 0, sales = 3, view = 5)
        insertDaily(1, "2026-07-26", like = 2, sales = 0, view = 0)
        insertDaily(2, "2026-07-21", like = 5, sales = 0, view = 0)
        insertDaily(1, "2026-07-19", like = 99, sales = 99, view = 99) // 전주(W29) 일요일
        insertDaily(2, "2026-07-27", like = 99, sales = 99, view = 99) // 차주(W31) 월요일
    }

    @AfterEach
    fun tearDown() {
        cleanTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("일간 집계를 ISO 주(월~일) 범위로 합산해 MV 테이블과 Redis ZSET에 적재한다 (경계 밖 제외)")
    @Test
    fun aggregatesWeeklyRankingWithinIsoWeek() {
        val jobExecution = launchWeeklyJob()

        assertAll(
            { assertThat(jobExecution.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(weeklyCount()).isEqualTo(2L) },
            { assertThat(weeklyColumn(1, "like_count")).isEqualTo(3) },
            { assertThat(weeklyColumn(1, "sales_count")).isEqualTo(5) },
            { assertThat(weeklyColumn(1, "view_count")).isEqualTo(15) },
            { assertThat(weeklyScore(1)).isCloseTo(5.6, within(1e-9)) },
            { assertThat(weeklyColumn(2, "like_count")).isEqualTo(5) },
            { assertThat(weeklyScore(2)).isCloseTo(1.0, within(1e-9)) },
            { assertThat(zsetScore(1)).isCloseTo(5.6, within(1e-9)) },
            { assertThat(zsetScore(2)).isCloseTo(1.0, within(1e-9)) },
            { assertThat(zsetOrderDesc()).containsExactly("1", "2") },
        )
    }

    @DisplayName("beforeStep에서 기존 주간 ZSET을 비우고 재적재해, 이전 실행에 남은 상품이 정리된다")
    @Test
    fun beforeStepClearsStaleZsetMembers() {
        redisTemplate.opsForZSet().add(zsetKey, "999", 999.0) // 지난 실행의 유령 멤버

        launchWeeklyJob()

        assertAll(
            { assertThat(zsetScore(999)).isNull() },
            { assertThat(zsetOrderDesc()).containsExactly("1", "2") },
        )
    }

    private fun launchWeeklyJob(): WeeklyRunResult {
        jobLauncherTestUtils.job = job
        val params = JobParametersBuilder()
            .addString("baseDate", baseDate.toString())
            .addLong("testRunId", System.nanoTime()) // 실행마다 JobInstance 분리
            .toJobParameters()
        val execution = jobLauncherTestUtils.launchJob(params)
        return WeeklyRunResult(execution.exitStatus.exitCode)
    }

    private data class WeeklyRunResult(val exitCode: String)

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
        jdbcTemplate.update("DELETE FROM product_metrics_weekly")
    }

    private fun weeklyCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_metrics_weekly WHERE year_week = ?",
            Long::class.java,
            yearWeek,
        )!!

    private fun weeklyColumn(productId: Long, column: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT $column FROM product_metrics_weekly WHERE product_id = ? AND year_week = ?",
            Int::class.java,
            productId,
            yearWeek,
        )!!

    private fun weeklyScore(productId: Long): Double =
        jdbcTemplate.queryForObject(
            "SELECT score FROM product_metrics_weekly WHERE product_id = ? AND year_week = ?",
            Double::class.java,
            productId,
            yearWeek,
        )!!

    private fun zsetScore(productId: Long): Double? =
        redisTemplate.opsForZSet().score(zsetKey, productId.toString())

    private fun zsetOrderDesc(): List<String> =
        redisTemplate.opsForZSet().reverseRange(zsetKey, 0, -1)?.toList() ?: emptyList()
}
