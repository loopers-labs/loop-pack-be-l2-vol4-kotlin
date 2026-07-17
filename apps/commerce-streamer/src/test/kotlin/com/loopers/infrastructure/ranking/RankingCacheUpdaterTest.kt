package com.loopers.infrastructure.ranking

import com.loopers.application.ranking.RankingKeyGenerator
import com.loopers.config.redis.RedisConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate

@SpringBootTest(classes = [RedisConfig::class, RedisTestContainersConfig::class, RedisCleanUp::class])
class RankingCacheUpdaterTest @Autowired constructor(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val cacheUpdater = RankingCacheUpdater(redisTemplate)

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    fun `절대 점수를 Redis sorted set 에 ZADD 로 반영한다`() {
        val date = LocalDate.of(2026, 7, 14)
        val scores = mapOf(
            DailyProductKey(1L, date) to 0.5,
            DailyProductKey(2L, date) to 1.2,
        )

        cacheUpdater.updateScores(scores)

        val key = RankingKeyGenerator.daily(date)
        assertThat(redisTemplate.opsForZSet().score(key, "1")).isEqualTo(0.5)
        assertThat(redisTemplate.opsForZSet().score(key, "2")).isEqualTo(1.2)
    }

    @Test
    fun `같은 상품에 대해 나중에 반영된 점수로 덮어쓴다`() {
        val date = LocalDate.of(2026, 7, 14)
        val key = RankingKeyGenerator.daily(date)

        cacheUpdater.updateScores(mapOf(DailyProductKey(1L, date) to 0.3))
        cacheUpdater.updateScores(mapOf(DailyProductKey(1L, date) to 0.2))

        assertThat(redisTemplate.opsForZSet().score(key, "1")).isEqualTo(0.2)
    }

    @Test
    fun `서로 다른 날짜의 스코어는 별도 키에 반영된다`() {
        val date1 = LocalDate.of(2026, 7, 14)
        val date2 = LocalDate.of(2026, 7, 15)

        cacheUpdater.updateScores(
            mapOf(
                DailyProductKey(1L, date1) to 0.5,
                DailyProductKey(1L, date2) to 0.8,
            ),
        )

        assertThat(redisTemplate.opsForZSet().score(RankingKeyGenerator.daily(date1), "1")).isEqualTo(0.5)
        assertThat(redisTemplate.opsForZSet().score(RankingKeyGenerator.daily(date2), "1")).isEqualTo(0.8)
    }
}
