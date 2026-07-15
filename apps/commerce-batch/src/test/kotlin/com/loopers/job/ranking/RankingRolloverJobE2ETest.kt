package com.loopers.job.ranking

import com.loopers.batch.job.ranking.RankingRolloverJobConfig
import com.loopers.config.redis.RedisConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = ["spring.batch.job.name=${RankingRolloverJobConfig.JOB_NAME}"])
class RankingRolloverJobE2ETest @Autowired constructor(
    // IDE 정적 분석 상 [SpringBatchTest] 의 주입보다 [SpringBootTest] 의 주입이 우선되어, 해당 컴포넌트는 없으므로 오류처럼 보일 수 있음.
    // [SpringBatchTest] 자체가 Scope 기반으로 주입하기 때문에 정상 동작함.
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    @param:Qualifier(RankingRolloverJobConfig.JOB_NAME) private val job: Job,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) masterTemplate: RedisTemplate<*, *>,
    private val redisCleanUp: RedisCleanUp,
) {
    @Suppress("UNCHECKED_CAST")
    private val redis = masterTemplate as RedisTemplate<String, String>

    private val sourceDate = LocalDate.of(2026, 7, 14)
    private val snapshotKey = "ranking:snapshot:20260714"
    private val targetAllKey = "ranking:all:20260715"
    private val targetSnapshotKey = "ranking:snapshot:20260715"
    private val statusKey = "ranking:rollover:status:20260715"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private fun jobParameters(): JobParameters = JobParametersBuilder()
        .addString("requestDate", sourceDate.toString())
        .addLong("runId", System.nanoTime())
        .toJobParameters()

    @DisplayName("snapshot:{D}의 점수를 floor(×0.1)해 all/snapshot:{D+1}에 이월하고, status를 DONE으로 전이한다.")
    @Test
    fun carriesOverScores_withFloorAndSkipZero() {
        jobLauncherTestUtils.job = job
        redis.opsForZSet().add(snapshotKey, "101", 1280.0) // → 128
        redis.opsForZSet().add(snapshotKey, "102", 55.0) // → floor(5.5) = 5
        redis.opsForZSet().add(snapshotKey, "103", 5.0) // → floor(0.5) = 0 → 소멸

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters())

        val vanishedScore: Double? = redis.opsForZSet().score(targetAllKey, "103")
        assertAll(
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(redis.opsForZSet().score(targetAllKey, "101")).isEqualTo(128.0) },
            { assertThat(redis.opsForZSet().score(targetSnapshotKey, "101")).isEqualTo(128.0) },
            { assertThat(redis.opsForZSet().score(targetAllKey, "102")).isEqualTo(5.0) },
            { assertThat(vanishedScore).isNull() },
            { assertThat(redis.opsForValue().get(statusKey)).isEqualTo("DONE") },
            { assertThat(redis.getExpire(statusKey)).isGreaterThan(600L).isLessThanOrEqualTo(2 * 24 * 60 * 60L) },
        )
    }

    @DisplayName("collector가 23:50~24:00 이중 적재로 이미 심어둔 실시간 이월분 위에 배치 이월분이 누적된다.")
    @Test
    fun accumulatesOnRealtimeCarryOver() {
        jobLauncherTestUtils.job = job
        redis.opsForZSet().add(snapshotKey, "101", 1000.0) // 23:50까지 누적분 → 100
        redis.opsForZSet().add(targetAllKey, "101", 7.0) // 23:50 이후 실시간 이월분(0.1×w×delta)

        jobLauncherTestUtils.launchJob(jobParameters())

        assertThat(redis.opsForZSet().score(targetAllKey, "101")).isEqualTo(107.0)
    }

    @DisplayName("D+1 보드에 TTL(2일)이 설정된다.")
    @Test
    fun setsTtlOnTargetBoards() {
        jobLauncherTestUtils.job = job
        redis.opsForZSet().add(snapshotKey, "101", 1280.0)

        jobLauncherTestUtils.launchJob(jobParameters())

        assertThat(redis.getExpire(targetAllKey)).isGreaterThan(0L).isLessThanOrEqualTo(2 * 24 * 60 * 60L)
        assertThat(redis.getExpire(targetSnapshotKey)).isGreaterThan(0L).isLessThanOrEqualTo(2 * 24 * 60 * 60L)
    }

    @DisplayName("다른 주체가 status를 선점(PROGRESS)하고 있으면, 이월 없이 정상 종료한다.")
    @Test
    fun skipsCarryOver_whenStatusHeldByOther() {
        jobLauncherTestUtils.job = job
        redis.opsForValue().set(statusKey, "PROGRESS")
        redis.opsForZSet().add(snapshotKey, "101", 1280.0)

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters())

        assertAll(
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(redis.hasKey(targetAllKey)).isFalse() },
            { assertThat(redis.opsForValue().get(statusKey)).isEqualTo("PROGRESS") },
        )
    }

    @DisplayName("이미 DONE인 날짜에 재실행해도 status 선점에 실패해 중복 이월하지 않는다.")
    @Test
    fun skipsCarryOver_whenAlreadyDone() {
        jobLauncherTestUtils.job = job
        redis.opsForValue().set(statusKey, "DONE")
        redis.opsForZSet().add(snapshotKey, "101", 1280.0)

        val jobExecution = jobLauncherTestUtils.launchJob(jobParameters())

        assertAll(
            { assertThat(jobExecution.exitStatus.exitCode).isEqualTo(ExitStatus.COMPLETED.exitCode) },
            { assertThat(redis.hasKey(targetAllKey)).isFalse() },
            { assertThat(redis.opsForValue().get(statusKey)).isEqualTo("DONE") },
        )
    }
}
