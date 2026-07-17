package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScoreDelta
import com.loopers.domain.ranking.RankingScoreEntry
import com.loopers.domain.ranking.RankingWindow
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class RankingRedisRepositoryIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private val window = RankingWindow(
        dailyKey = "ranking:all:v1:20260717",
        hourlyKey = "ranking:hourly:v1:2026071714",
        dailyExpireAt = Instant.now().plus(2, ChronoUnit.DAYS),
        hourlyExpireAt = Instant.now().plus(2, ChronoUnit.HOURS),
    )

    @DisplayName("엔트리의 델타가 daily/hourly ZSET에 모두 가산되고 TTL이 설정된다.")
    @Test
    fun appliesDeltasToBothWindows() {
        val applied = rankingRepository.applyAll(
            listOf(
                RankingScoreEntry("evt-1", listOf(RankingScoreDelta(productId = 10L, score = 0.1))),
                RankingScoreEntry("evt-2", listOf(RankingScoreDelta(productId = 10L, score = 0.2))),
            ),
            window,
        )

        assertThat(applied).isEqualTo(2)
        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "10")).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-9))
        assertThat(redisTemplate.opsForZSet().score(window.hourlyKey, "10")).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-9))
        assertThat(redisTemplate.getExpire(window.dailyKey)).isGreaterThan(0L)
        assertThat(redisTemplate.getExpire(window.hourlyKey)).isGreaterThan(0L)
    }

    @DisplayName("같은 eventId 재적용은 걸러져 점수가 중복 가산되지 않는다 — SETNX dedup.")
    @Test
    fun deduplicatesByEventId() {
        val entries = listOf(RankingScoreEntry("evt-dup", listOf(RankingScoreDelta(10L, 0.5))))

        val first = rankingRepository.applyAll(entries, window)
        val second = rankingRepository.applyAll(entries, window)

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(0)
        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "10")).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9))
    }

    @DisplayName("음수 델타(좋아요 취소)는 점수를 감산한다.")
    @Test
    fun negativeDeltaDecreasesScore() {
        rankingRepository.applyAll(listOf(RankingScoreEntry("evt-a", listOf(RankingScoreDelta(10L, 0.2)))), window)
        rankingRepository.applyAll(listOf(RankingScoreEntry("evt-b", listOf(RankingScoreDelta(10L, -0.2)))), window)

        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "10")).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9))
    }
}
