package com.loopers.infrastructure.ranking

import com.loopers.config.kafka.KafkaTopics
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@EmbeddedKafka(
    partitions = 3,
    topics = [KafkaTopics.CATALOG_EVENTS, KafkaTopics.ORDER_EVENTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class RedisRankingRepositoryIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() = redisCleanUp.truncateAll()

    private val date = LocalDate.of(2026, 7, 16)

    private fun key(): String = "ranking:" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    @DisplayName("addScore 는 Lua 로 ZINCRBY 와 EXPIRE 를 원자적으로 적용한다")
    @Test
    fun addScoreAppliesScoreAndTtl() {
        rankingRepository.addScore(date, 42L, 0.7)

        assertThat(masterTemplate.opsForZSet().score(key(), "42")).isCloseTo(0.7, within(1e-9))
        assertThat(masterTemplate.getExpire(key())).isGreaterThan(0)
    }

    @DisplayName("addScore 를 여러 번 호출하면 점수가 누적된다")
    @Test
    fun addScoreAccumulates() {
        rankingRepository.addScore(date, 42L, 0.7)
        rankingRepository.addScore(date, 42L, 0.2)

        assertThat(masterTemplate.opsForZSet().score(key(), "42")).isCloseTo(0.9, within(1e-9))
    }
}
