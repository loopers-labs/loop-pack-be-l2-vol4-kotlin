package com.loopers.job.productranking

import com.loopers.batch.job.productranking.WeeklyProductRankingJobConfig
import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.productrank.ProductRankWeeklyRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersInvalidException
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import kotlin.math.ln

@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@SpringBootTest
@SpringBatchTest
@TestPropertySource(
    properties = [
        "spring.batch.job.name=${WeeklyProductRankingJobConfig.JOB_NAME}",
        "spring.batch.job.enabled=false",
        "commerce.product-ranking.batch.metric.chunk-size=1",
    ],
)
class WeeklyProductRankingJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(WeeklyProductRankingJobConfig.JOB_NAME) private val job: Job,
    private val jdbcTemplate: JdbcTemplate,
    private val productRankWeeklyRepository: ProductRankWeeklyRepository,
    @param:Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val baseDate = LocalDate.parse("2026-08-03")

    @BeforeEach
    fun setUp() {
        jobLauncherTestUtils.job = job
        createDailyMetricTable()
        jdbcTemplate.execute("TRUNCATE TABLE product_metric_daily")
    }

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        databaseCleanUp.truncateAllTables()
        jdbcTemplate.execute("DROP TABLE IF EXISTS product_metric_daily")
    }

    @DisplayName("weeklyProductRankingJob은 직전 주 daily metric을 집계하고 기간 총 판매금액으로 MV score를 계산한다")
    @Test
    fun aggregatesWeeklyMetricsAndMaterializesScores() {
        redisTemplate.opsForHash<String, String>().putAll(
            RankingRedisKeys.ACTIVE_WEIGHTS,
            mapOf("view" to "1.0", "like" to "10.0", "sales" to "100.0"),
        )
        redisTemplate.opsForZSet().add(RankingRedisKeys.weekly(baseDate), "stale", 1.0)
        insertDailyMetric(baseDate.minusDays(7), 10L, viewCount = 1L, likeCount = 1L, salesAmount = 100L)
        insertDailyMetric(baseDate.minusDays(1), 10L, viewCount = 2L, likeCount = 0L, salesAmount = 200L)
        insertDailyMetric(baseDate.minusDays(8), 10L, viewCount = 100L, likeCount = 100L, salesAmount = 100_000L)
        insertDailyMetric(baseDate, 10L, viewCount = 100L, likeCount = 100L, salesAmount = 100_000L)

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters(runId = 1_001L))

        val rank = productRankWeeklyRepository.findTop100(baseDate).single()
        val publicationCount = publishedWeeklyCount(baseDate)
        val expectedScore = 3.0 + 10.0 + ln(301.0) * 100.0
        assertAll(
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(rank.productId).isEqualTo(10L) },
            { assertThat(rank.rankingScore).isCloseTo(expectedScore, within(0.000_001)) },
            { assertThat(publicationCount).isEqualTo(1) },
            { assertThat(redisTemplate.hasKey(RankingRedisKeys.weekly(baseDate))).isTrue() },
        )
    }

    @DisplayName("weeklyProductRankingJob은 동일 baseDate 재실행 시 기존 결과를 삭제하고 productId 오름차순으로 동점을 정렬한다")
    @Test
    fun rerunsIdempotentlyAndOrdersTiesByProductId() {
        redisTemplate.opsForHash<String, String>().putAll(
            RankingRedisKeys.ACTIVE_WEIGHTS,
            mapOf("view" to "1.0", "like" to "0.0", "sales" to "0.0"),
        )
        redisTemplate.opsForZSet().add(RankingRedisKeys.carry(baseDate.minusDays(1)), "5", 99_999.0)
        insertDailyMetric(baseDate.minusDays(7), 5L, viewCount = 10L, likeCount = 0L, salesAmount = 0L)
        insertDailyMetric(baseDate.minusDays(6), 10L, viewCount = 10L, likeCount = 0L, salesAmount = 0L)
        insertDailyMetric(baseDate.minusDays(5), 1L, viewCount = 9L, likeCount = 0L, salesAmount = 0L)

        jobLauncherTestUtils.launchJob(jobParameters(runId = 2_001L))
        productRankWeeklyRepository.upsert(baseDate, 5L, rankingScore = 999.0)
        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters(runId = 2_002L))

        val top = productRankWeeklyRepository.findTop100(baseDate)
        assertAll(
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(top.map { it.productId }).containsExactly(5L, 10L, 1L) },
            { assertThat(top.map { it.rankingScore }).containsExactly(10.0, 10.0, 9.0) },
        )
    }

    @DisplayName("weeklyProductRankingJob은 baseDate가 월요일이 아니면 실패한다")
    @Test
    fun failsWhenBaseDateIsNotMonday() {
        assertThatThrownBy {
            jobLauncherTestUtils.launchJob(jobParameters(baseDate = LocalDate.parse("2026-08-04"), runId = 3_001L))
        }.isInstanceOf(JobParametersInvalidException::class.java)
    }

    private fun jobParameters(
        baseDate: LocalDate = this.baseDate,
        runId: Long,
    ) = JobParametersBuilder()
            .addLocalDate("baseDate", baseDate)
            .addLong("run.id", runId)
            .toJobParameters()

    private fun createDailyMetricTable() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS product_metric_daily (
                id BIGINT NOT NULL AUTO_INCREMENT,
                metric_date DATE NOT NULL,
                product_id BIGINT NOT NULL,
                view_count BIGINT NOT NULL DEFAULT 0,
                like_count BIGINT NOT NULL DEFAULT 0,
                sales_amount BIGINT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL,
                updated_at DATETIME NOT NULL,
                deleted_at DATETIME NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_product_metric_daily_date_product (metric_date, product_id),
                KEY idx_product_metric_daily_product_date (product_id, metric_date)
            )
            """.trimIndent(),
        )
    }

    private fun publishedWeeklyCount(baseDate: LocalDate): Int {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM product_rank_publication
            WHERE period = 'WEEKLY'
              AND base_date = ?
              AND generation_id IS NOT NULL
            """.trimIndent(),
            Int::class.java,
            baseDate,
        ) ?: 0
    }

    private fun insertDailyMetric(
        metricDate: LocalDate,
        productId: Long,
        viewCount: Long,
        likeCount: Long,
        salesAmount: Long,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO product_metric_daily (
                metric_date,
                product_id,
                view_count,
                like_count,
                sales_amount,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """.trimIndent(),
            metricDate,
            productId,
            viewCount,
            likeCount,
            salesAmount,
        )
    }
}
