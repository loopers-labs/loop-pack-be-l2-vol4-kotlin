package com.loopers.job.ranking

import com.loopers.batch.job.ranking.DailyRankingCarryOverJobConfig
import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
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
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.LocalDate

@Import(RedisTestContainersConfig::class)
@SpringBootTest
@SpringBatchTest
@TestPropertySource(
    properties = [
        "spring.batch.job.name=${DailyRankingCarryOverJobConfig.JOB_NAME}",
        "spring.batch.job.enabled=false",
    ],
)
class DailyRankingCarryOverJobE2ETest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(DailyRankingCarryOverJobConfig.JOB_NAME) private val job: Job,
    @param:Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val baseDate = LocalDate.parse("2026-08-06")
    private val sourceDate = baseDate.minusDays(1)

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("dailyRankingCarryOverJob은 baseDate 전날 TOP 100만 target carry와 최종 랭킹으로 이월한다")
    @Test
    fun carriesPreviousDayTop100ToBaseDate() {
        seedSourceRanking()

        val exitCode = launchCarryOverJob(runId = 1_001L)

        assertAll(
            { assertThat(exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(redisTemplate.opsForZSet().zCard(RankingRedisKeys.carry(baseDate)) ?: -1L).isEqualTo(100L) },
            { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.carry(baseDate), "1") == null).isTrue() },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.carry(baseDate), "101") ?: Double.NaN)
                    .isCloseTo(10.1, within(1e-12))
            },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.all(baseDate), "101") ?: Double.NaN)
                    .isCloseTo(10.1, within(1e-12))
            },
            { assertThat(redisTemplate.getExpire(RankingRedisKeys.carry(baseDate))).isPositive() },
            { assertThat(redisTemplate.getExpire(RankingRedisKeys.all(baseDate))).isPositive() },
        )
    }

    @DisplayName("dailyRankingCarryOverJob은 동일 baseDate 중복 실행 시 최종 결과가 동일하다")
    @Test
    fun rerunsIdempotently() {
        seedSourceRanking()
        launchCarryOverJob(runId = 2_001L)
        redisTemplate.opsForZSet().add(RankingRedisKeys.carry(baseDate), "101", 999.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(baseDate), "101", 999.0)

        val exitCode = launchCarryOverJob(runId = 2_002L)

        assertAll(
            { assertThat(exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(redisTemplate.opsForZSet().zCard(RankingRedisKeys.carry(baseDate)) ?: -1L).isEqualTo(100L) },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.carry(baseDate), "101") ?: Double.NaN)
                    .isCloseTo(10.1, within(1e-12))
            },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.all(baseDate), "101") ?: Double.NaN)
                    .isCloseTo(10.1, within(1e-12))
            },
        )
    }

    @DisplayName("dailyRankingCarryOverJob은 분산 락을 얻지 못하면 target을 변경하지 않는다")
    @Test
    fun doesNotMutateTargetWhenLockIsNotAcquired() {
        seedSourceRanking()
        redisTemplate.opsForValue().set(RankingRedisKeys.carryOverLock(sourceDate), "owner-a", Duration.ofSeconds(60))
        redisTemplate.opsForZSet().add(RankingRedisKeys.carry(baseDate), "existing", 7.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(baseDate), "existing", 7.0)

        val exitCode = launchCarryOverJob(runId = 3_001L)

        assertAll(
            { assertThat(exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(redisTemplate.opsForZSet().zCard(RankingRedisKeys.carry(baseDate)) ?: -1L).isEqualTo(1L) },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.carry(baseDate), "existing") ?: Double.NaN)
                    .isEqualTo(7.0)
            },
            { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.carry(baseDate), "101") == null).isTrue() },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.all(baseDate), "existing") ?: Double.NaN)
                    .isEqualTo(7.0)
            },
        )
    }

    @DisplayName("dailyRankingCarryOverJob은 baseDate가 없으면 실패한다")
    @Test
    fun failsWhenBaseDateIsMissing() {
        jobLauncherTestUtils.job = job

        assertThatThrownBy {
            jobLauncherTestUtils.launchJob(
                JobParametersBuilder()
                    .addLong("run.id", 4_001L)
                    .toJobParameters(),
            )
        }.isInstanceOf(JobParametersInvalidException::class.java)
    }

    private fun seedSourceRanking() {
        (1L..101L).forEach { productId ->
            redisTemplate.opsForZSet().add(
                RankingRedisKeys.all(sourceDate),
                productId.toString(),
                productId.toDouble(),
            )
        }
    }

    private fun launchCarryOverJob(runId: Long): String =
        jobLauncherTestUtils.apply { job = this@DailyRankingCarryOverJobE2ETest.job }
            .launchJob(
                JobParametersBuilder()
                    .addLocalDate("baseDate", baseDate)
                    .addLong("run.id", runId)
                    .toJobParameters(),
            ).exitStatus.exitCode
}
