package com.loopers.infrastructure.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.redis.RedisConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * infrastructure 어댑터가 내부적으로 사용하는 캐시 저장소.
 *
 * 상위 계층(domain/application/interfaces)은 이 클래스의 존재를 모른다. 캐시 적중/적재/무효화/
 * 개수 상한(ZSET eviction)을 모두 여기에 캡슐화한다.
 *
 * - 읽기(GET): Replica 우선 템플릿
 * - 쓰기(SET/DEL/ZADD/eviction): Master 템플릿
 * - 개수 상한: ZSET + Lua 로 원자 처리 ([putWithLimit])
 * - 직렬화: [ObjectMapper] JSON 문자열 (기존 String 직렬화 템플릿 재사용)
 */
@Component
class RedisCacheStore(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    masterTemplate: RedisTemplate<*, *>,
    replicaTemplate: RedisTemplate<*, *>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    private val master = masterTemplate as RedisTemplate<String, String>

    @Suppress("UNCHECKED_CAST")
    private val replica = replicaTemplate as RedisTemplate<String, String>

    private val evictScript = DefaultRedisScript(PUT_WITH_LIMIT_LUA, Long::class.java)

    /**
     * 캐시 조회. 적중 시 ZSET score 를 현재 시각으로 갱신(LRU touch)한다.
     * 역직렬화 실패는 캐시 미스로 간주하고 null 을 반환한다(깨진 캐시가 장애로 번지지 않도록).
     */
    fun <T> get(key: String, zsetKey: String, type: TypeReference<T>): T? {
        val raw = try {
            replica.opsForValue().get(key)
        } catch (e: Exception) {
            log.warn("캐시 조회 실패 key={}", key, e)
            null
        } ?: return null

        return try {
            val value = objectMapper.readValue(raw, type)
            touch(zsetKey, key)
            value
        } catch (e: Exception) {
            log.warn("캐시 역직렬화 실패 key={}", key, e)
            null
        }
    }

    /**
     * 캐시 적재 + 개수 상한 eviction 을 단일 Lua 로 원자 실행한다.
     * 직렬화/적재 실패는 삼키고 로깅만 한다(캐시는 부가 기능이므로 원본 흐름을 막지 않는다).
     */
    fun putWithLimit(key: String, value: Any, ttl: Duration, zsetKey: String, limit: Long) {
        val json = try {
            objectMapper.writeValueAsString(value)
        } catch (e: Exception) {
            log.warn("캐시 직렬화 실패 key={}", key, e)
            return
        }
        try {
            master.execute(
                evictScript,
                listOf(key, zsetKey),
                json,
                ttl.seconds.toString(),
                System.currentTimeMillis().toString(),
                limit.toString(),
            )
        } catch (e: Exception) {
            log.warn("캐시 적재 실패 key={}", key, e)
        }
    }

    /** 무효화: 데이터 키 DEL + ZSET member 제거. */
    fun evict(key: String, zsetKey: String) {
        try {
            master.delete(key)
            master.opsForZSet().remove(zsetKey, key)
        } catch (e: Exception) {
            log.warn("캐시 무효화 실패 key={}", key, e)
        }
    }

    private fun touch(zsetKey: String, key: String) {
        try {
            master.opsForZSet().add(zsetKey, key, System.currentTimeMillis().toDouble())
        } catch (e: Exception) {
            log.warn("캐시 ZSET touch 실패 key={}", key, e)
        }
    }

    companion object {
        /**
         * KEYS[1]=dataKey, KEYS[2]=zsetKey
         * ARGV[1]=value, ARGV[2]=ttlSeconds, ARGV[3]=score, ARGV[4]=limit
         *
         * ZSET member 는 dataKey 자체이므로, 초과분은 ZSET 에서 꺼낸 member 를 그대로 DEL 한다.
         */
        private const val PUT_WITH_LIMIT_LUA = """
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[3], KEYS[1])
            local n = redis.call('ZCARD', KEYS[2])
            local limit = tonumber(ARGV[4])
            if n > limit then
                local overflow = n - limit
                local victims = redis.call('ZRANGE', KEYS[2], 0, overflow - 1)
                for _, victim in ipairs(victims) do
                    redis.call('DEL', victim)
                end
                redis.call('ZREMRANGEBYRANK', KEYS[2], 0, overflow - 1)
            end
            return n
        """
    }
}
