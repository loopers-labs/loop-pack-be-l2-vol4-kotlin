package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RankingDatePolicy
import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.domain.ranking.CatalogRankingMetric
import com.loopers.domain.ranking.CatalogRankingProjection
import com.loopers.domain.ranking.OrderRankingProjection
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate
import java.time.ZoneId

@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.kafka.listener.auto-startup=false"],
)
class RedisRankingProjectionRepositoryTest
    @Autowired
    constructor(
        private val repository: RedisRankingProjectionRepository,
        private val redisTemplate: RedisTemplate<String, String>,
        private val redisCleanUp: RedisCleanUp,
    ) {
        private val properties = RankingRedisProperties()
        private val datePolicy = RankingDatePolicy(properties)

        @BeforeEach
        fun setUp() {
            redisCleanUp.truncateAll()
        }

        @DisplayName("Catalog과 Order 이벤트를 멱등하게 일별 랭킹으로 반영한다")
        @Test
        fun projectsDailyRankingIdempotently() {
            val date = LocalDate.now(ZoneId.of("Asia/Seoul"))
            val expiresAt = datePolicy.expiresAt(date)
            val productId = 10L

            val viewed = CatalogRankingProjection("view-1", productId, date, CatalogRankingMetric.VIEW, 1, expiresAt)
            repository.projectCatalog(viewed)
            repository.projectCatalog(viewed)
            repository.projectCatalog(CatalogRankingProjection("like-1", productId, date, CatalogRankingMetric.LIKE, 1, expiresAt))
            repository.projectCatalog(CatalogRankingProjection("unlike-1", productId, date, CatalogRankingMetric.LIKE, -1, expiresAt))
            repository.projectCatalog(CatalogRankingProjection("unlike-2", productId, date, CatalogRankingMetric.LIKE, -1, expiresAt))
            repository.projectOrder(
                OrderRankingProjection("order-1", date, listOf(OrderRankingProjection.SalesItem(productId, 200)), expiresAt),
            )
            repository.projectOrder(
                OrderRankingProjection("order-2", date, listOf(OrderRankingProjection.SalesItem(productId, 100)), expiresAt),
            )

            val expectedScore = 0.05 - 0.4 + kotlin.math.ln(301.0)
            val expireAtSeconds = redisTemplate.getExpire(RankingRedisKeys.all(date), java.util.concurrent.TimeUnit.SECONDS)

            assertAll(
                { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.view(date), productId.toString()) ?: Double.NaN).isEqualTo(1.0) },
                { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.like(date), productId.toString()) ?: Double.NaN).isEqualTo(-1.0) },
                { assertThat(redisTemplate.opsForHash<String, String>().get(RankingRedisKeys.rawSalesAmount(date), productId.toString())).isEqualTo("300") },
                { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.sales(date), productId.toString()) ?: Double.NaN).isCloseTo(kotlin.math.ln(301.0), within(0.000_001)) },
                { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.all(date), productId.toString()) ?: Double.NaN).isCloseTo(expectedScore, within(0.000_001)) },
                { assertThat(redisTemplate.opsForSet().size(RankingRedisKeys.processed(date)) ?: -1L).isEqualTo(6L) },
                { assertThat(expireAtSeconds).isBetween(1L, 2L * 24L * 60L * 60L) },
            )
        }
    }
