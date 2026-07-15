package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRolloverPort
import com.loopers.domain.ranking.RankingRolloverStatus
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest
class RankingRolloverAdapterIntegrationTest @Autowired constructor(
    private val rankingRolloverPort: RankingRolloverPort,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) masterTemplate: RedisTemplate<*, *>,
    private val redisCleanUp: RedisCleanUp,
) {
    @Suppress("UNCHECKED_CAST")
    private val master = masterTemplate as RedisTemplate<String, String>

    private val yesterday = LocalDate.of(2026, 7, 14)
    private val today = LocalDate.of(2026, 7, 15)
    private val statusKey = "ranking:rollover:status:20260715"
    private val notifiedKey = "ranking:rollover:notified:20260715"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("getStatus는 status 값(DONE/PROGRESS/없음)을 3-way로 판별한다.")
    @Test
    fun resolvesStatusThreeWay() {
        assertThat(rankingRolloverPort.getStatus(today)).isEqualTo(RankingRolloverStatus.NOT_STARTED)

        master.opsForValue().set(statusKey, "PROGRESS")
        assertThat(rankingRolloverPort.getStatus(today)).isEqualTo(RankingRolloverStatus.IN_PROGRESS)

        master.opsForValue().set(statusKey, "DONE")
        assertThat(rankingRolloverPort.getStatus(today)).isEqualTo(RankingRolloverStatus.DONE)
    }

    @DisplayName("tryStart는 SET NX 선점 - 최초 1회만 성공하고, PROGRESS와 TTL이 기록된다.")
    @Test
    fun tryStartActsAsDistributedLock() {
        val first = rankingRolloverPort.tryStart(today)
        val second = rankingRolloverPort.tryStart(today)

        assertAll(
            { assertThat(first).isTrue() },
            { assertThat(second).isFalse() },
            { assertThat(master.opsForValue().get(statusKey)).isEqualTo("PROGRESS") },
            { assertThat(master.getExpire(statusKey, TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(600L) },
        )
    }

    @DisplayName("carryOverSnapshot은 floor(×0.1)로 이월하고 0점은 소멸시키며, D+1 보드에 TTL을 설정한다.")
    @Test
    fun carriesOverWithFloorAndSkipZero() {
        val fromKey = "ranking:snapshot:20260714"
        val toAllKey = "ranking:all:20260715"
        val toSnapshotKey = "ranking:snapshot:20260715"
        master.opsForZSet().add(fromKey, "101", 1280.0) // → 128
        master.opsForZSet().add(fromKey, "102", 55.0) // → floor(5.5) = 5
        master.opsForZSet().add(fromKey, "103", 5.0) // → floor(0.5) = 0 → 소멸

        rankingRolloverPort.carryOverSnapshot(fromDate = yesterday, toDate = today)

        val vanishedScore: Double? = master.opsForZSet().score(toAllKey, "103")
        assertAll(
            { assertThat(master.opsForZSet().score(toAllKey, "101")).isEqualTo(128.0) },
            { assertThat(master.opsForZSet().score(toSnapshotKey, "101")).isEqualTo(128.0) },
            { assertThat(master.opsForZSet().score(toAllKey, "102")).isEqualTo(5.0) },
            { assertThat(vanishedScore).isNull() },
            { assertThat(master.getExpire(toAllKey, TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(2 * 24 * 60 * 60L) },
            { assertThat(master.getExpire(toSnapshotKey, TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(2 * 24 * 60 * 60L) },
        )
    }

    @DisplayName("carryOverSnapshot은 페이지 순회 시 PROGRESS TTL을 갱신한다(heartbeat) - 짧게 남은 TTL이 10분으로 늘어난다.")
    @Test
    fun refreshesProgressTtlAsHeartbeat() {
        master.opsForValue().set(statusKey, "PROGRESS", java.time.Duration.ofSeconds(30))
        master.opsForZSet().add("ranking:snapshot:20260714", "101", 1280.0)

        rankingRolloverPort.carryOverSnapshot(fromDate = yesterday, toDate = today)

        assertThat(master.getExpire(statusKey, TimeUnit.SECONDS)).isGreaterThan(30L).isLessThanOrEqualTo(600L)
    }

    @DisplayName("complete는 status를 DONE으로 덮어쓴다(TTL 2일) - 별도 락 해제가 필요 없다.")
    @Test
    fun completeOverwritesStatusToDone() {
        rankingRolloverPort.tryStart(today)

        rankingRolloverPort.complete(today)

        assertAll(
            { assertThat(master.opsForValue().get(statusKey)).isEqualTo("DONE") },
            { assertThat(master.getExpire(statusKey, TimeUnit.SECONDS)).isGreaterThan(600L).isLessThanOrEqualTo(2 * 24 * 60 * 60L) },
        )
    }

    @DisplayName("tryMarkNotified는 SET NX 가드 - 최초 1회만 true를 반환해 WARN 로그 중복을 막는다.")
    @Test
    fun notifiedGuardAllowsOnlyFirst() {
        val first = rankingRolloverPort.tryMarkNotified(today)
        val second = rankingRolloverPort.tryMarkNotified(today)

        assertAll(
            { assertThat(first).isTrue() },
            { assertThat(second).isFalse() },
            { assertThat(master.getExpire(notifiedKey, TimeUnit.SECONDS)).isGreaterThan(0L).isLessThanOrEqualTo(24 * 60 * 60L) },
        )
    }
}
