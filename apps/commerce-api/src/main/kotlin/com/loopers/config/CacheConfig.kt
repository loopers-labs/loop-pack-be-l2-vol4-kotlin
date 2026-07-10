package com.loopers.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.product.application.ProductDetailInfo
import com.loopers.product.application.ProductDetailReader
import com.loopers.product.interfaces.ProductListQuery
import com.loopers.product.interfaces.ProductListResponse
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext

@Configuration
@EnableCaching
class CacheConfig : CachingConfigurer {
    private val logger = LoggerFactory.getLogger(CacheConfig::class.java)

    @Bean
    fun redisCacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisCacheManager {
        val cacheDefaults = RedisCacheConfiguration.defaultCacheConfig().disableCachingNullValues()
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(cacheDefaults)
            .withCacheConfiguration(
                ProductListQuery.CACHE_NAME,
                cacheDefaults.entryTtl(Duration.ofSeconds(5))
                    .serializeValuesWith(serializerFor(objectMapper, ProductListResponse::class.java)),
            )
            .withCacheConfiguration(
                ProductDetailReader.CACHE_NAME,
                cacheDefaults.entryTtl(Duration.ofMinutes(1))
                    .serializeValuesWith(serializerFor(objectMapper, ProductDetailInfo::class.java)),
            )
            .build()
    }

    override fun errorHandler(): CacheErrorHandler = object : CacheErrorHandler {
        override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) {
            logger.warn("캐시 조회 실패 — 원본 조회로 진행. cache={}, cause={}", cache.name, exception.javaClass.simpleName)
        }

        override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) {
            logger.warn("캐시 저장 실패 — 원본 응답으로 진행. cache={}, cause={}", cache.name, exception.javaClass.simpleName)
        }

        override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) {
            logger.warn("캐시 무효화 실패. cache={}, cause={}", cache.name, exception.javaClass.simpleName)
        }

        override fun handleCacheClearError(exception: RuntimeException, cache: Cache) {
            logger.warn("캐시 전체 삭제 실패. cache={}, cause={}", cache.name, exception.javaClass.simpleName)
        }
    }

    private fun <T> serializerFor(
        objectMapper: ObjectMapper,
        type: Class<T>,
    ): RedisSerializationContext.SerializationPair<T> =
        RedisSerializationContext.SerializationPair.fromSerializer(Jackson2JsonRedisSerializer(objectMapper, type))
}
