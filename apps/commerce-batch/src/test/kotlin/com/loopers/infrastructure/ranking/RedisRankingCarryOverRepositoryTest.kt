package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RankingDatePolicy
import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingCarryOverRepository
import com.loopers.domain.ranking.RankingWeights
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
class RedisRankingCarryOverRepositoryTest @Autowired constructor(
    private val repository: RankingCarryOverRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val properties: RankingRedisProperties,
    private val redisCleanUp: RedisCleanUp,
) {
    private val datePolicy = RankingDatePolicy(RankingRedisProperties())

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("source 상위 100개 점수의 10%만 target carry와 최종 랭킹으로 복사한다")
    @Test
    fun carriesTop100ToTargetDate() {
        val sourceDate = LocalDate.of(2026, 8, 5)
        val targetDate = sourceDate.plusDays(1)
        (1L..101L).forEach { productId ->
            redisTemplate.opsForZSet().add(
                RankingRedisKeys.all(sourceDate),
                productId.toString(),
                productId.toDouble(),
            )
        }

        val carriedCount = repository.carryOver(
            sourceDate = sourceDate,
            targetDate = targetDate,
            topN = properties.carryOver.topN,
            factor = properties.carryOver.factor,
            defaultWeights = RankingWeights(
                view = properties.viewWeight,
                like = properties.likeWeight,
                sales = properties.salesWeight,
            ),
            expiresAt = datePolicy.expiresAt(targetDate),
        )

        assertAll(
            { assertThat(carriedCount).isEqualTo(100L) },
            { assertThat(redisTemplate.opsForZSet().zCard(RankingRedisKeys.carry(targetDate)) ?: -1L).isEqualTo(100L) },
            { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.carry(targetDate), "1") == null).isTrue() },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.carry(targetDate), "101") ?: Double.NaN)
                    .isCloseTo(10.1, within(1e-12))
            },
            {
                assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.all(targetDate), "101") ?: Double.NaN)
                    .isCloseTo(10.1, within(1e-12))
            },
            { assertThat(redisTemplate.getExpire(RankingRedisKeys.carry(targetDate))).isPositive() },
            { assertThat(redisTemplate.getExpire(RankingRedisKeys.all(targetDate))).isPositive() },
        )
    }

    @DisplayName("carry lock은 한 소유자만 획득하고 소유자만 해제할 수 있다")
    @Test
    fun allowsOnlyOwnerToReleaseLock() {
        val date = LocalDate.of(2026, 7, 14)

        assertAll(
            { assertThat(repository.tryAcquireLock(date, "owner-a", Duration.ofSeconds(60))).isTrue() },
            { assertThat(repository.tryAcquireLock(date, "owner-b", Duration.ofSeconds(60))).isFalse() },
        )
        repository.releaseLock(date, "owner-b")
        assertThat(repository.tryAcquireLock(date, "owner-b", Duration.ofSeconds(60))).isFalse()

        repository.releaseLock(date, "owner-a")
        assertThat(repository.tryAcquireLock(date, "owner-b", Duration.ofSeconds(60))).isTrue()
    }
}
