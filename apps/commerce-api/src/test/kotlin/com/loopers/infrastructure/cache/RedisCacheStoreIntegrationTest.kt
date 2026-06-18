package com.loopers.infrastructure.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.config.redis.RedisConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.Duration

@Suppress("UNCHECKED_CAST")
@SpringBootTest
class RedisCacheStoreIntegrationTest @Autowired constructor(
    private val cacheStore: RedisCacheStore,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    masterTemplate: RedisTemplate<*, *>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val redis = masterTemplate as RedisTemplate<String, String>
    private val type = object : TypeReference<ProductCacheModel>() {}
    private val zsetKey = "cache:zset:test"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private fun model(id: Long) =
        ProductCacheModel(id = id, name = "p$id", price = 100L * id, description = "d", brandId = 1L)

    @DisplayName("putWithLimit 후 get 하면 동일한 값이 복원된다.")
    @Test
    fun putAndGet() {
        val key = "product:1"
        cacheStore.putWithLimit(key, model(1L), Duration.ofMinutes(10), zsetKey, 100L)

        val restored = cacheStore.get(key, zsetKey, type)
        assertThat(restored).isEqualTo(model(1L))
    }

    @DisplayName("putWithLimit 시 TTL이 설정된다.")
    @Test
    fun setsTtl() {
        val key = "product:1"
        cacheStore.putWithLimit(key, model(1L), Duration.ofHours(1), zsetKey, 100L)

        val ttl = redis.getExpire(key)
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(3600L)
    }

    @DisplayName("evict 하면 데이터 키와 ZSET member가 함께 제거된다.")
    @Test
    fun evicts() {
        val key = "product:1"
        cacheStore.putWithLimit(key, model(1L), Duration.ofMinutes(10), zsetKey, 100L)

        cacheStore.evict(key, zsetKey)

        assertThat(cacheStore.get(key, zsetKey, type)).isNull()
        val score: Double? = redis.opsForZSet().score(zsetKey, key)
        assertThat(score).isNull()
    }

    @DisplayName("개수 상한을 초과하면 가장 오래된 키가 ZSET과 함께 제거된다.")
    @Test
    fun evictsOldestWhenOverLimit() {
        val limit = 2L
        // score(=접근시각)가 구분되도록 사이에 간격을 둔다.
        cacheStore.putWithLimit("product:1", model(1L), Duration.ofMinutes(10), zsetKey, limit)
        Thread.sleep(5)
        cacheStore.putWithLimit("product:2", model(2L), Duration.ofMinutes(10), zsetKey, limit)
        Thread.sleep(5)
        cacheStore.putWithLimit("product:3", model(3L), Duration.ofMinutes(10), zsetKey, limit)

        assertThat(redis.opsForZSet().zCard(zsetKey)).isEqualTo(2L)
        assertThat(cacheStore.get("product:1", zsetKey, type)).isNull()
        assertThat(redis.opsForValue().get("product:1")).isNull()
        assertThat(cacheStore.get("product:2", zsetKey, type)).isEqualTo(model(2L))
        assertThat(cacheStore.get("product:3", zsetKey, type)).isEqualTo(model(3L))
    }
}
