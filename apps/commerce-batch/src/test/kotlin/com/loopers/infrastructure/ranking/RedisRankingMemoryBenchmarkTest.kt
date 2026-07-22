package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RankingDatePolicy
import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingCarryOverRepository
import com.loopers.domain.ranking.RankingWeights
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.assertAll
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Tag("memory")
@Import(RedisTestContainersConfig::class)
@SpringBootTest
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
class RedisRankingMemoryBenchmarkTest @Autowired constructor(
    private val carryOverRepository: RankingCarryOverRepository,
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

    @DisplayName("10만 상품 랭킹 key별 MEMORY USAGE와 상위 100 carry 메모리를 측정한다")
    @Test
    fun measuresMemoryForHundredThousandProducts(testReporter: TestReporter) {
        val date = LocalDate.of(2026, 7, 14)
        val tomorrow = date.plusDays(1)
        val rankingKeys = listOf(
            RankingRedisKeys.view(date),
            RankingRedisKeys.like(date),
            RankingRedisKeys.sales(date),
            RankingRedisKeys.all(date),
            RankingRedisKeys.rawSalesAmount(date),
            RankingRedisKeys.processed(date),
        )
        val usedMemoryBefore = usedMemory()

        (1L..PRODUCT_COUNT).chunked(PIPELINE_CHUNK_SIZE).forEach { productIds ->
            redisTemplate.executePipelined(
                RedisCallback<Any?> { connection ->
                    productIds.forEach { productId ->
                        val member = productId.toString().toByteArray()
                        val score = productId.toDouble()
                        connection.zSetCommands().zAdd(rankingKeys[0].toByteArray(), score, member)
                        connection.zSetCommands().zAdd(rankingKeys[1].toByteArray(), score, member)
                        connection.zSetCommands().zAdd(rankingKeys[2].toByteArray(), score, member)
                        connection.zSetCommands().zAdd(rankingKeys[3].toByteArray(), score, member)
                        connection.hashCommands().hSet(rankingKeys[4].toByteArray(), member, productId.toString().toByteArray())
                        connection.setCommands().sAdd(rankingKeys[5].toByteArray(), "event-$productId".toByteArray())
                    }
                    null
                },
            )
        }

        val memoryByKey = rankingKeys.associateWith(::memoryUsage)
        val usedMemoryAfter = usedMemory()
        val totalKeyBytes = memoryByKey.values.sum()
        val usedMemoryDelta = usedMemoryAfter - usedMemoryBefore
        val estimatedBytesPerProduct = totalKeyBytes.toDouble() / PRODUCT_COUNT

        carryOverRepository.carryOver(
            sourceDate = date,
            targetDate = tomorrow,
            topN = properties.carryOver.topN,
            factor = properties.carryOver.factor,
            defaultWeights = RankingWeights(properties.viewWeight, properties.likeWeight, properties.salesWeight),
            expiresAt = datePolicy.expiresAt(tomorrow),
        )
        val carryKey = RankingRedisKeys.carry(tomorrow)
        val carryBytes = memoryUsage(carryKey)
        val allRankingBytes = memoryByKey.getValue(RankingRedisKeys.all(date))

        memoryByKey.forEach { (key, bytes) -> testReporter.publishEntry("MEMORY USAGE $key", "$bytes bytes") }
        testReporter.publishEntry("key bytes total", "$totalKeyBytes bytes")
        testReporter.publishEntry("used_memory delta", "$usedMemoryDelta bytes")
        testReporter.publishEntry("estimated bytes/product", "%.2f bytes".format(estimatedBytesPerProduct))
        testReporter.publishEntry("MEMORY USAGE $carryKey", "$carryBytes bytes")
        log.info(
            "Ranking memory benchmark: memoryByKey={}, totalKeyBytes={}, usedMemoryDelta={}, estimatedBytesPerProduct={}, carryBytes={}",
            memoryByKey,
            totalKeyBytes,
            usedMemoryDelta,
            "%.2f".format(estimatedBytesPerProduct),
            carryBytes,
        )

        assertAll(
            { assertThat(memoryByKey.values).allMatch { it > 0L } },
            { assertThat(usedMemoryDelta).isPositive() },
            { assertThat(redisTemplate.opsForZSet().zCard(carryKey) ?: -1L).isEqualTo(100L) },
            { assertThat(carryBytes).isLessThan(allRankingBytes) },
        )
    }

    private fun memoryUsage(key: String): Long {
        return redisTemplate.execute(
            RedisCallback<Long> { connection ->
                @Suppress("UNCHECKED_CAST")
                val commands = connection.nativeConnection as RedisClusterAsyncCommands<ByteArray, ByteArray>
                commands.memoryUsage(key.toByteArray()).get(5, TimeUnit.SECONDS)
            },
        ) ?: 0L
    }

    private fun usedMemory(): Long {
        return redisTemplate.connectionFactory?.connection.use { connection ->
            connection?.serverCommands()
                ?.info("memory")
                ?.getProperty("used_memory")
                ?.toLong() ?: 0L
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RedisRankingMemoryBenchmarkTest::class.java)
        private const val PRODUCT_COUNT = 100_000L
        private const val PIPELINE_CHUNK_SIZE = 1_000
    }
}
